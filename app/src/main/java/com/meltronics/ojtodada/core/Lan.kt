package com.meltronics.ojtodada.core

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface

/** Local network helpers: own IP addresses + automatic discovery (mDNS / NSD). */
object Lan {
    private const val TYPE = "_ojdada._tcp."

    private var nsd: NsdManager? = null
    private var regListener: NsdManager.RegistrationListener? = null
    private var discListener: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    private val resolveQueue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false

    /** IPv4 addresses on Wi-Fi / hotspot interfaces (mobile data interfaces skipped). */
    fun localIps(): List<String> {
        val skip = listOf("rmnet", "ccmni", "dummy", "tun", "v4-", "clat", "ppp", "lo")
        val prefer = listOf("wlan", "swlan", "ap", "softap", "p2p", "eth")
        return runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback && skip.none { s -> it.name.startsWith(s) } }
                .sortedBy { ni -> prefer.indexOfFirst { ni.name.startsWith(it) }.let { if (it < 0) 99 else it } }
                .flatMap { ni -> ni.inetAddresses.toList().filterIsInstance<Inet4Address>().map { it.hostAddress!! } }
                .distinct()
        }.getOrDefault(emptyList())
    }

    fun register(ctx: Context, name: String, id: String, port: Int) {
        val mgr = nsd ?: ctx.getSystemService(NsdManager::class.java).also { nsd = it }
        unregister()
        val info = NsdServiceInfo().apply {
            serviceName = name.take(50)
            serviceType = TYPE
            this.port = port
            setAttribute("id", id)
            setAttribute("n", name.take(60))
        }
        val l = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(i: NsdServiceInfo) {}
            override fun onRegistrationFailed(i: NsdServiceInfo, e: Int) {}
            override fun onServiceUnregistered(i: NsdServiceInfo) {}
            override fun onUnregistrationFailed(i: NsdServiceInfo, e: Int) {}
        }
        regListener = l
        runCatching { mgr.registerService(info, NsdManager.PROTOCOL_DNS_SD, l) }
    }

    fun unregister() {
        regListener?.let { l -> runCatching { nsd?.unregisterService(l) } }
        regListener = null
    }

    fun discover(ctx: Context, selfId: String, onFound: (Peer) -> Unit, onLost: (String) -> Unit) {
        val mgr = nsd ?: ctx.getSystemService(NsdManager::class.java).also { nsd = it }
        stopDiscovery()
        runCatching {
            val wm = ctx.applicationContext.getSystemService(WifiManager::class.java)
            multicastLock = wm.createMulticastLock("ojdada-nsd").apply { setReferenceCounted(false); acquire() }
        }
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(t: String) {}
            override fun onDiscoveryStopped(t: String) {}
            override fun onStartDiscoveryFailed(t: String, e: Int) {}
            override fun onStopDiscoveryFailed(t: String, e: Int) {}
            override fun onServiceFound(info: NsdServiceInfo) = enqueue(mgr, info, selfId, onFound)
            override fun onServiceLost(info: NsdServiceInfo) = onLost("lan:" + info.serviceName)
        }
        discListener = l
        runCatching { mgr.discoverServices(TYPE, NsdManager.PROTOCOL_DNS_SD, l) }
    }

    fun stopDiscovery() {
        discListener?.let { l -> runCatching { nsd?.stopServiceDiscovery(l) } }
        discListener = null
        runCatching { multicastLock?.release() }
        multicastLock = null
        synchronized(resolveQueue) { resolveQueue.clear(); resolving = false }
    }

    // NsdManager can only resolve one service at a time, so queue them.
    private fun enqueue(mgr: NsdManager, info: NsdServiceInfo, selfId: String, onFound: (Peer) -> Unit) {
        synchronized(resolveQueue) {
            resolveQueue.addLast(info)
            if (resolving) return
            resolving = true
        }
        next(mgr, selfId, onFound)
    }

    @Suppress("DEPRECATION")
    private fun next(mgr: NsdManager, selfId: String, onFound: (Peer) -> Unit) {
        val info = synchronized(resolveQueue) {
            resolveQueue.removeFirstOrNull() ?: run { resolving = false; null }
        } ?: return
        runCatching {
            mgr.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(i: NsdServiceInfo, e: Int) = next(mgr, selfId, onFound)
                override fun onServiceResolved(i: NsdServiceInfo) {
                    val id = i.attributes["id"]?.toString(Charsets.UTF_8)
                    val name = i.attributes["n"]?.toString(Charsets.UTF_8) ?: i.serviceName
                    val host = i.host?.hostAddress
                    if (id != selfId && host != null && i.host is Inet4Address) {
                        onFound(Peer("lan:" + i.serviceName, name, Route.Lan(listOf(host), i.port), "Wi-Fi"))
                    }
                    next(mgr, selfId, onFound)
                }
            })
        }.onFailure { next(mgr, selfId, onFound) }
    }
}
