package com.meltronics.ojtodada.core

import com.meltronics.ojtodada.service.TransferService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.UUID
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Cross-platform sharing with NO app on the other side.
 * Windows, Mac, Linux, iPhone, iPad, Chromebook (or any phone) open the page in a
 * browser over the local Wi-Fi / offline hotspot to download and upload files.
 * Everything is served from the phone itself; the page has no external resources.
 */
object WebShare {
    val running = MutableStateFlow(false)
    val shared = MutableStateFlow<List<ShareItem>>(emptyList())
    var port = 0
        private set
    var token = ""
        private set
    private var server: ServerSocket? = null
    private var job: Job? = null

    fun start() {
        if (running.value) return
        token = UUID.randomUUID().toString().replace("-", "").take(8)
        val ss = ServerSocket()
        ss.reuseAddress = true
        try { ss.bind(InetSocketAddress(8080)) } catch (e: Exception) { ss.bind(InetSocketAddress(0)) }
        server = ss
        port = ss.localPort
        running.value = true
        TransferService.ensure(Engine.ctx)
        job = Engine.scope.launch {
            while (isActive) {
                val s = runCatching { ss.accept() }.getOrNull() ?: break
                launch { runCatching { handle(s) }; runCatching { s.close() } }
            }
        }
    }

    fun stop() {
        runCatching { server?.close() }
        server = null
        job?.cancel()
        job = null
        running.value = false
    }

    fun urls(): List<String> = Lan.localIps().map { "http://$it:$port/?t=$token" }

    fun add(items: List<ShareItem>) = shared.update { cur -> (cur + items).distinctBy { it.uri } }
    fun remove(item: ShareItem) = shared.update { cur -> cur.filter { it != item } }

    // ------------------------------------------------------------------ HTTP

    private class Req(val method: String, val path: String, val query: Map<String, String>, val headers: Map<String, String>)

    private fun readLine(inp: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val c = inp.read()
            if (c < 0) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(c.toChar())
            if (sb.length > 16384) return null
        }
    }

    private fun parseQuery(q: String): Map<String, String> = q.split('&').filter { it.isNotEmpty() }.associate {
        val k = it.substringBefore('=')
        val v = it.substringAfter('=', "")
        URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
    }

    private fun handle(sock: Socket) {
        sock.tcpNoDelay = true
        sock.soTimeout = 0
        val inp = BufferedInputStream(sock.getInputStream(), 64 * 1024)
        val out = BufferedOutputStream(sock.getOutputStream(), Wire.BUF)
        val line = readLine(inp) ?: return
        val parts = line.split(' ')
        if (parts.size < 2) return
        val headers = mutableMapOf<String, String>()
        while (true) {
            val h = readLine(inp) ?: return
            if (h.isEmpty()) break
            val i = h.indexOf(':')
            if (i > 0) headers[h.substring(0, i).trim().lowercase()] = h.substring(i + 1).trim()
        }
        val target = parts[1]
        val req = Req(parts[0].uppercase(), target.substringBefore('?'), parseQuery(target.substringAfter('?', "")), headers)
        val client = sock.inetAddress?.hostAddress ?: "browser"

        if (req.path == "/favicon.ico") return respond(out, 204, "text/plain", ByteArray(0))
        if (req.query["t"] != token) return respond(out, 403, "text/html; charset=utf-8", WebPage.denied().toByteArray())

        when {
            req.method == "GET" && (req.path == "/" || req.path == "/index.html") ->
                respond(out, 200, "text/html; charset=utf-8", WebPage.html(Engine.deviceName.value, token).toByteArray())
            req.method == "GET" && req.path == "/api/list" -> respond(out, 200, "application/json", listJson().toByteArray())
            req.method == "GET" && req.path.startsWith("/d/") -> download(out, req, client)
            req.method == "GET" && req.path == "/zip" -> zipAll(out, client)
            (req.method == "PUT" || req.method == "POST") && req.path == "/api/up" -> upload(inp, out, req, client)
            else -> respond(out, 404, "text/plain", "Not found".toByteArray())
        }
    }

    private fun respond(out: OutputStream, code: Int, type: String, body: ByteArray, extra: String = "") {
        val status = when (code) { 200 -> "OK"; 204 -> "No Content"; 403 -> "Forbidden"; 404 -> "Not Found"; 411 -> "Length Required"; else -> "Error" }
        out.write(("HTTP/1.1 $code $status\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\n" +
            "Cache-Control: no-store\r\nConnection: close\r\n$extra\r\n").toByteArray())
        out.write(body)
        out.flush()
    }

    private fun listJson(): String {
        val arr = JSONArray()
        shared.value.forEachIndexed { i, it ->
            arr.put(JSONObject().put("i", i).put("name", it.name).put("size", it.size).put("hsize", Fmt.bytes(it.size)))
        }
        val got = JSONArray()
        Engine.received.value.filter { it.from.startsWith("Browser") }.take(30).forEach {
            got.put(JSONObject().put("name", it.name).put("hsize", Fmt.bytes(it.size)))
        }
        return JSONObject().put("device", Engine.deviceName.value).put("files", arr).put("received", got).toString()
    }

    private fun disposition(name: String): String {
        val ascii = name.replace(Regex("[^\\x20-\\x7E]"), "_").replace("\"", "")
        val enc = URLEncoder.encode(name, "UTF-8").replace("+", "%20")
        return "Content-Disposition: attachment; filename=\"$ascii\"; filename*=UTF-8''$enc\r\n"
    }

    private fun download(out: OutputStream, req: Req, client: String) {
        val idx = req.path.removePrefix("/d/").substringBefore('/').toIntOrNull()
        val item = idx?.let { shared.value.getOrNull(it) } ?: return respond(out, 404, "text/plain", "Gone".toByteArray())
        val id = UUID.randomUUID().toString().take(10)
        Engine.track(Transfer(id, "Browser $client", Direction.SEND, "Browser", 1, item.name, 0, item.size, TStatus.ACTIVE))
        try {
            val src = Engine.ctx.contentResolver.openInputStream(item.uri) ?: throw java.io.IOException("Cannot read file")
            out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Length: ${item.size}\r\n" +
                disposition(item.name) + "Connection: close\r\n\r\n").toByteArray())
            var done = 0L
            val meter = Meter()
            src.use {
                Wire.copy(it, out, item.size) { n ->
                    done += n
                    if (meter.add(n)) { val d = done; Engine.trackEdit(id) { t -> t.copy(bytesDone = d, speedBps = meter.speed) } }
                }
            }
            Engine.trackEdit(id) { it.copy(status = TStatus.DONE, bytesDone = item.size, speedBps = 0) }
        } catch (e: Exception) {
            Engine.trackEdit(id) { it.copy(status = TStatus.FAILED, speedBps = 0, note = "Browser stopped the download") }
        }
    }

    private fun zipAll(out: OutputStream, client: String) {
        val items = shared.value
        val total = items.sumOf { it.size }
        val id = UUID.randomUUID().toString().take(10)
        Engine.track(Transfer(id, "Browser $client", Direction.SEND, "Browser", items.size, "OJ-to-Dada.zip", 0, total, TStatus.ACTIVE))
        try {
            // No Content-Length: the zip is streamed and the connection closes at the end.
            out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/zip\r\n" + disposition("OJ-to-Dada.zip") +
                "Connection: close\r\n\r\n").toByteArray())
            val zip = ZipOutputStream(out)
            zip.setLevel(Deflater.NO_COMPRESSION) // fast; most files (apk, mp4, jpg, zip) don't compress anyway
            var done = 0L
            val meter = Meter()
            val used = mutableSetOf<String>()
            items.forEach { item ->
                var n = item.name
                var k = 1
                while (!used.add(n)) n = "${item.name.substringBeforeLast('.')} (${k++}).${item.name.substringAfterLast('.', "bin")}"
                zip.putNextEntry(ZipEntry(n))
                Engine.ctx.contentResolver.openInputStream(item.uri)?.use { src ->
                    Wire.copy(src, zip, item.size) { c ->
                        done += c
                        if (meter.add(c)) { val d = done; Engine.trackEdit(id) { t -> t.copy(bytesDone = d, speedBps = meter.speed, currentName = item.name) } }
                    }
                }
                zip.closeEntry()
            }
            zip.finish()
            zip.flush()
            Engine.trackEdit(id) { it.copy(status = TStatus.DONE, bytesDone = total, speedBps = 0) }
        } catch (e: Exception) {
            Engine.trackEdit(id) { it.copy(status = TStatus.FAILED, speedBps = 0, note = "Browser stopped the download") }
        }
    }

    private fun upload(inp: InputStream, out: OutputStream, req: Req, client: String) {
        val len = req.headers["content-length"]?.toLongOrNull()
            ?: return respond(out, 411, "text/plain", "Length required".toByteArray())
        if (req.headers["expect"]?.lowercase() == "100-continue") {
            out.write("HTTP/1.1 100 Continue\r\n\r\n".toByteArray()); out.flush()
        }
        val name = FileStore.cleanName(req.query["name"] ?: "upload")
        val id = UUID.randomUUID().toString().take(10)
        Engine.track(Transfer(id, "Browser $client", Direction.RECEIVE, "Browser", 1, name, 0, len, TStatus.ACTIVE))
        val sink = FileStore.create(Engine.ctx, name)
        try {
            var done = 0L
            val meter = Meter()
            Wire.copy(inp, sink.out, len) { n ->
                done += n
                if (meter.add(n)) { val d = done; Engine.trackEdit(id) { t -> t.copy(bytesDone = d, speedBps = meter.speed) } }
            }
            sink.commit()
            Engine.addReceived(ReceivedFile(name, sink.uri.toString(), len, FileStore.mimeFor(name), "Browser $client", System.currentTimeMillis()))
            Engine.trackEdit(id) { it.copy(status = TStatus.DONE, bytesDone = len, speedBps = 0) }
            respond(out, 200, "application/json", JSONObject().put("ok", true).put("name", name).toString().toByteArray())
        } catch (e: Exception) {
            sink.abort()
            Engine.trackEdit(id) { it.copy(status = TStatus.FAILED, speedBps = 0, note = "Upload interrupted") }
        }
    }
}
