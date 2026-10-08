package com.meltronics.ojtodada.core

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Network
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.meltronics.ojtodada.MainActivity
import com.meltronics.ojtodada.service.TransferService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.net.SocketFactory

/**
 * The heart of OJ-to-Dada. Always listening (TCP + Bluetooth) so the phone can
 * receive while it is sending, and every send / receive runs in its own coroutine,
 * so any number of devices can transfer at the same time.
 */
@SuppressLint("StaticFieldLeak") // holds the application context only
object Engine {
    const val DEFAULT_PORT = 48720
    private const val OFFER_TIMEOUT_MS = 120_000L
    private const val CHANNEL = "transfers"

    lateinit var ctx: Context
        private set
    private lateinit var prefs: SharedPreferences
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val deviceName = MutableStateFlow("")
    lateinit var deviceId: String
        private set
    var port = DEFAULT_PORT
        private set

    private val _transfers = MutableStateFlow<List<Transfer>>(emptyList())
    val transfers: StateFlow<List<Transfer>> = _transfers

    private val _received = MutableStateFlow<List<ReceivedFile>>(emptyList())
    val received: StateFlow<List<ReceivedFile>> = _received

    private val _offers = MutableStateFlow<List<IncomingOffer>>(emptyList())
    val offers: StateFlow<List<IncomingOffer>> = _offers
    private val offerAnswers = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    private val lanPeers = MutableStateFlow<Map<String, Peer>>(emptyMap())
    private val btPeers = MutableStateFlow<Map<String, Peer>>(emptyMap())
    val nearbyLan: StateFlow<Map<String, Peer>> = lanPeers
    val nearbyBt: StateFlow<Map<String, Peer>> = btPeers

    /** "Visible to nearby devices" switch; keeps the background service alive. */
    val receiveMode = MutableStateFlow(false)
    val autoAccept = MutableStateFlow(false)

    private val cancels = ConcurrentHashMap<String, () -> Unit>()
    private val cancelled = ConcurrentHashMap.newKeySet<String>()
    private var tcpServer: ServerSocket? = null

    fun init(app: Context) {
        ctx = app.applicationContext
        prefs = ctx.getSharedPreferences("oj", Context.MODE_PRIVATE)
        deviceId = prefs.getString("id", null) ?: UUID.randomUUID().toString().take(12).also { prefs.edit().putString("id", it).apply() }
        deviceName.value = prefs.getString("name", null)
            ?: runCatching { Settings.Global.getString(ctx.contentResolver, "device_name") }.getOrNull()
            ?: (Build.MANUFACTURER.replaceFirstChar { it.uppercase() } + " " + Build.MODEL)
        autoAccept.value = prefs.getBoolean("auto", false)
        loadHistory()
        createChannel()
        startTcpServer()
        restartBluetoothServer()
    }

    fun rename(name: String) {
        val n = name.trim().take(40).ifBlank { return }
        deviceName.value = n
        prefs.edit().putString("name", n).apply()
        Lan.register(ctx, n, deviceId, port)
    }

    fun setAutoAccept(v: Boolean) {
        autoAccept.value = v
        prefs.edit().putBoolean("auto", v).apply()
    }

    var onboarded: Boolean
        get() = prefs.getBoolean("onboarded", false)
        set(v) { prefs.edit().putBoolean("onboarded", v).apply() }

    // ------------------------------------------------------------------ servers

    private fun startTcpServer() {
        if (tcpServer != null) return
        val ss = ServerSocket()
        ss.reuseAddress = true
        try {
            ss.bind(InetSocketAddress(DEFAULT_PORT))
        } catch (e: IOException) {
            ss.bind(InetSocketAddress(0))
        }
        ss.receiveBufferSize = 1 shl 20
        tcpServer = ss
        port = ss.localPort
        Lan.register(ctx, deviceName.value, deviceId, port)
        scope.launch {
            while (isActive) {
                val s = runCatching { ss.accept() }.getOrNull() ?: break
                launch {
                    s.tcpNoDelay = true
                    s.sendBufferSize = 1 shl 20
                    val who = s.inetAddress?.hostAddress ?: "Wi-Fi"
                    receive(Link(s.getInputStream(), s.getOutputStream()) { s.close() }, "Wi-Fi", who)
                }
            }
        }
    }

    fun restartBluetoothServer() {
        Bt.serve(ctx, scope) { link, who -> receive(link, "Bluetooth", who) }
    }

    // ------------------------------------------------------------------ discovery

    fun startDiscovery() {
        lanPeers.value = emptyMap()
        Lan.discover(ctx, deviceId,
            onFound = { p -> lanPeers.update { it + (p.key to p) } },
            onLost = { k -> lanPeers.update { it - k } })
    }

    fun scanBluetooth() {
        Bt.startScan(ctx) { p -> btPeers.update { it + (p.key to p) } }
    }

    fun stopDiscovery() {
        Lan.stopDiscovery()
        Bt.stopScan(ctx)
    }

    /** Text for the receive QR: our IPs, port and (if running) the offline hotspot details. */
    fun receiveQr(): String = QrCodec.receiverCode(deviceName.value, deviceId, Lan.localIps(), port, Hotspot.info.value)

    // ------------------------------------------------------------------ transfers state

    private fun newId() = UUID.randomUUID().toString().take(10)

    private fun put(t: Transfer) = _transfers.update { list ->
        val i = list.indexOfFirst { it.id == t.id }
        if (i < 0) listOf(t) + list else list.toMutableList().also { it[i] = t }
    }

    private fun edit(id: String, f: (Transfer) -> Transfer) = _transfers.update { list ->
        list.map { if (it.id == id) f(it) else it }
    }

    fun cancel(id: String) {
        cancelled.add(id)
        cancels.remove(id)?.invoke()
        edit(id) { if (it.isLive) it.copy(status = TStatus.CANCELLED, speedBps = 0) else it }
    }

    fun clearFinished() = _transfers.update { l -> l.filter { it.isLive } }

    private fun fail(id: String, e: Throwable) {
        val wasCancelled = cancelled.remove(id)
        edit(id) {
            if (!it.isLive) it
            else if (wasCancelled) it.copy(status = TStatus.CANCELLED, speedBps = 0)
            else it.copy(status = TStatus.FAILED, speedBps = 0, note = e.message ?: e.javaClass.simpleName)
        }
    }

    /** Lets the web server report browser transfers in the same list. */
    internal fun track(t: Transfer) = put(t)
    internal fun trackEdit(id: String, f: (Transfer) -> Transfer) = edit(id, f)
    internal fun trackCancel(id: String, closer: () -> Unit) { cancels[id] = closer }
    internal fun trackDone(id: String) { cancels.remove(id); cancelled.remove(id) }

    // ------------------------------------------------------------------ sending

    fun send(items: List<ShareItem>, peers: List<Peer>) {
        if (items.isEmpty() || peers.isEmpty()) return
        TransferService.ensure(ctx)
        peers.forEach { p -> scope.launch { sendTo(items, p) } }
    }

    private suspend fun sendTo(items: List<ShareItem>, peer: Peer) {
        val id = newId()
        val total = items.sumOf { it.size }
        put(Transfer(id, peer.name, Direction.SEND, peer.via, items.size, "Connecting…", 0, total, TStatus.WAITING))
        var link: Link? = null
        try {
            val l = open(peer)
            link = l
            cancels[id] = { l.close() }
            val out = DataOutputStream(BufferedOutputStream(l.output, Wire.BUF))
            val inp = DataInputStream(BufferedInputStream(l.input, 64 * 1024))

            out.writeInt(Wire.MAGIC); out.writeInt(Wire.VERSION)
            Wire.writeJson(out, hello())
            if (inp.readInt() != Wire.MAGIC) throw IOException("That device is not running OJ-to-Dada")
            inp.readInt()
            val theirHello = Wire.readJson(inp)
            val theirName = theirHello.optString("name", peer.name)

            val files = JSONArray()
            items.forEach { files.put(JSONObject().put("name", it.name).put("size", it.size).put("mime", it.mime)) }
            Wire.writeJson(out, JSONObject().put("type", "offer").put("files", files))
            edit(id) { it.copy(peerName = theirName, currentName = "Waiting for $theirName to accept…") }

            val reply = Wire.readJson(inp)
            if (!reply.optBoolean("accept")) {
                edit(id) { it.copy(status = TStatus.DECLINED, currentName = "Declined by $theirName") }
                return
            }
            edit(id) { it.copy(status = TStatus.ACTIVE) }

            var done = 0L
            val meter = Meter()
            items.forEachIndexed { index, item ->
                edit(id) { it.copy(currentName = item.name) }
                Wire.writeJson(out, JSONObject().put("type", "file").put("index", index)
                    .put("name", item.name).put("size", item.size).put("mime", item.mime))
                val src = ctx.contentResolver.openInputStream(item.uri) ?: throw IOException("Cannot read ${item.name}")
                src.use {
                    Wire.copy(it, out, item.size) { n ->
                        done += n
                        if (meter.add(n)) {
                            val d = done
                            edit(id) { t -> t.copy(bytesDone = d, speedBps = meter.speed) }
                        }
                    }
                }
                val ack = Wire.readJson(inp)
                if (!ack.optBoolean("ok")) throw IOException("Receiver could not save ${item.name}")
            }
            Wire.writeJson(out, JSONObject().put("type", "done"))
            val d = done
            edit(id) { it.copy(status = TStatus.DONE, bytesDone = d, speedBps = 0, currentName = "Sent ${items.size} file(s)") }
        } catch (e: Throwable) {
            fail(id, e)
        } finally {
            cancels.remove(id)
            cancelled.remove(id)
            link?.close()
        }
    }

    private suspend fun open(peer: Peer): Link = when (val r = peer.route) {
        is Route.Bt -> Bt.connect(ctx, r.address)
        is Route.Lan -> openLan(r)
    }

    private suspend fun openLan(r: Route.Lan): Link {
        // 1) try directly (same Wi-Fi / already on their hotspot)
        tryHosts(r.hosts, r.port, null, 2500)?.let { return it }
        // 2) join their offline hotspot from the QR, then retry
        if (r.ssid != null) {
            val net = Hotspot.join(ctx, r.ssid, r.pass, r.security)
            repeat(6) {
                tryHosts(r.hosts, r.port, net, 4000)?.let { return it }
                kotlinx.coroutines.delay(1000)
            }
        }
        throw IOException("Can't reach the other device. Make sure both are on the same Wi-Fi or hotspot.")
    }

    private fun tryHosts(hosts: List<String>, port: Int, net: Network?, timeout: Int): Link? {
        for (h in hosts) {
            val f: SocketFactory = net?.socketFactory ?: SocketFactory.getDefault()
            val s: Socket = f.createSocket()
            try {
                s.tcpNoDelay = true
                s.sendBufferSize = 1 shl 20
                s.connect(InetSocketAddress(h, port), timeout)
                return Link(s.getInputStream(), s.getOutputStream()) { s.close() }
            } catch (_: Exception) {
                runCatching { s.close() }
            }
        }
        return null
    }

    private fun hello() = JSONObject()
        .put("type", "hello").put("name", deviceName.value).put("id", deviceId).put("platform", "android")

    // ------------------------------------------------------------------ receiving

    private suspend fun receive(link: Link, via: String, who: String) {
        val id = newId()
        var tracked = false
        try {
            val inp = DataInputStream(BufferedInputStream(link.input, Wire.BUF))
            val out = DataOutputStream(BufferedOutputStream(link.output, 64 * 1024))
            if (inp.readInt() != Wire.MAGIC) return
            inp.readInt()
            val theirHello = Wire.readJson(inp)
            out.writeInt(Wire.MAGIC); out.writeInt(Wire.VERSION)
            Wire.writeJson(out, hello())
            val from = theirHello.optString("name", who)

            val offer = Wire.readJson(inp)
            val arr = offer.getJSONArray("files")
            val files = (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                OfferFile(FileStore.cleanName(o.getString("name")), o.getLong("size"), o.optString("mime"))
            }
            val accepted = autoAccept.value || ask(IncomingOffer(id, from, via, files))
            Wire.writeJson(out, JSONObject().put("type", "reply").put("accept", accepted))
            if (!accepted) return

            TransferService.ensure(ctx)
            tracked = true
            put(Transfer(id, from, Direction.RECEIVE, via, files.size, files.firstOrNull()?.name ?: "", 0, files.sumOf { it.size }, TStatus.ACTIVE))
            cancels[id] = { link.close() }

            var done = 0L
            val meter = Meter()
            for (f in files) {
                val h = Wire.readJson(inp)
                val name = FileStore.cleanName(h.getString("name"))
                val size = h.getLong("size")
                edit(id) { it.copy(currentName = name) }
                val sink = FileStore.create(ctx, name)
                try {
                    Wire.copy(inp, sink.out, size) { n ->
                        done += n
                        if (meter.add(n)) {
                            val d = done
                            edit(id) { t -> t.copy(bytesDone = d, speedBps = meter.speed) }
                        }
                    }
                    sink.commit()
                } catch (e: Throwable) {
                    sink.abort()
                    throw e
                }
                addReceived(ReceivedFile(name, sink.uri.toString(), size, FileStore.mimeFor(name), from, System.currentTimeMillis()))
                Wire.writeJson(out, JSONObject().put("type", "ack").put("ok", true))
            }
            runCatching { Wire.readJson(inp) } // "done"
            val d = done
            edit(id) { it.copy(status = TStatus.DONE, bytesDone = d, speedBps = 0, currentName = "Received ${files.size} file(s)") }
        } catch (e: Throwable) {
            if (tracked) fail(id, e)
        } finally {
            cancels.remove(id)
            cancelled.remove(id)
            link.close()
        }
    }

    /** Shows the accept dialog (and a notification) and waits for the user. */
    private suspend fun ask(offer: IncomingOffer): Boolean {
        val answer = CompletableDeferred<Boolean>()
        offerAnswers[offer.id] = answer
        _offers.update { it + offer }
        notifyOffer(offer)
        val result = withTimeoutOrNull(OFFER_TIMEOUT_MS) { answer.await() } ?: false
        offerAnswers.remove(offer.id)
        _offers.update { l -> l.filter { it.id != offer.id } }
        cancelNotification(offer.id.hashCode())
        return result
    }

    fun answer(offerId: String, accept: Boolean) {
        offerAnswers[offerId]?.complete(accept)
    }

    // ------------------------------------------------------------------ history

    internal fun addReceived(f: ReceivedFile) {
        _received.update { (listOf(f) + it).take(300) }
        saveHistory()
    }

    fun forget(f: ReceivedFile) {
        _received.update { l -> l.filter { it != f } }
        saveHistory()
    }

    private fun saveHistory() {
        val arr = JSONArray()
        _received.value.forEach {
            arr.put(JSONObject().put("n", it.name).put("u", it.uri).put("s", it.size).put("m", it.mime).put("f", it.from).put("t", it.time))
        }
        prefs.edit().putString("history", arr.toString()).apply()
    }

    private fun loadHistory() {
        val raw = prefs.getString("history", null) ?: return
        runCatching {
            val arr = JSONArray(raw)
            _received.value = (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                ReceivedFile(o.getString("n"), o.getString("u"), o.getLong("s"), o.getString("m"), o.optString("f"), o.getLong("t"))
            }
        }
    }

    // ------------------------------------------------------------------ notifications

    private fun createChannel() {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Transfers", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(TransferService.CHANNEL, "Ready to share", NotificationManager.IMPORTANCE_LOW))
    }

    private fun canNotify() = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun notifyOffer(o: IncomingOffer) {
        if (!canNotify()) return
        val pi = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("${o.fromName} wants to send ${o.files.size} file(s)")
            .setContentText("${Fmt.bytes(o.total)} · tap to accept or decline")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(o.id.hashCode(), n)
    }

    private fun cancelNotification(id: Int) = ctx.getSystemService(NotificationManager::class.java).cancel(id)
}
