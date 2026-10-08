package com.meltronics.ojtodada.core

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Offline hotspot: the phone creates its own Wi-Fi network (Android "local-only hotspot").
 * No SIM, router or internet needed. Other phones join it from the QR code,
 * and PCs / iPhones join it by scanning the standard Wi-Fi QR with their camera.
 */
object Hotspot {
    val info = MutableStateFlow<HotspotInfo?>(null)
    val error = MutableStateFlow<String?>(null)
    private var reservation: WifiManager.LocalOnlyHotspotReservation? = null

    private val joined = mutableMapOf<String, Network>()
    private val callbacks = mutableListOf<ConnectivityManager.NetworkCallback>()

    @SuppressLint("MissingPermission")
    fun start(ctx: Context) {
        if (reservation != null) return
        error.value = null
        val wm = ctx.applicationContext.getSystemService(WifiManager::class.java)
        try {
            wm.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(r: WifiManager.LocalOnlyHotspotReservation) {
                    reservation = r
                    info.value = read(r)
                }

                override fun onStopped() {
                    reservation = null
                    info.value = null
                }

                override fun onFailed(reason: Int) {
                    reservation = null
                    info.value = null
                    error.value = when (reason) {
                        WifiManager.LocalOnlyHotspotCallback.ERROR_INCOMPATIBLE_MODE -> "Turn off your normal mobile hotspot / tethering first, then try again."
                        WifiManager.LocalOnlyHotspotCallback.ERROR_TETHERING_DISALLOWED -> "Hotspot is blocked on this phone."
                        WifiManager.LocalOnlyHotspotCallback.ERROR_NO_CHANNEL -> "No free Wi-Fi channel. Turn Wi-Fi off and on, then retry."
                        else -> "Could not start hotspot (code $reason). Make sure Location is ON and try again."
                    }
                }
            }, Handler(Looper.getMainLooper()))
        } catch (e: SecurityException) {
            error.value = "Permission needed: allow Nearby devices / Location for OJ-to-Dada."
        } catch (e: IllegalStateException) {
            error.value = "Hotspot already starting. Wait a moment."
        }
    }

    @Suppress("DEPRECATION")
    private fun read(r: WifiManager.LocalOnlyHotspotReservation): HotspotInfo {
        return if (Build.VERSION.SDK_INT >= 30) {
            val c = r.softApConfiguration
            HotspotInfo(c.ssid?.trim('"') ?: "", c.passphrase ?: "", c.securityType.coerceIn(1, 3))
        } else {
            val c: WifiConfiguration? = r.wifiConfiguration
            HotspotInfo(c?.SSID?.trim('"') ?: "", c?.preSharedKey?.trim('"') ?: "", 1)
        }
    }

    fun stop() {
        runCatching { reservation?.close() }
        reservation = null
        info.value = null
    }

    /** Standard Wi-Fi QR that iPhone, Android and Windows cameras understand. */
    fun wifiQr(h: HotspotInfo): String {
        fun esc(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace(":", "\\:").replace("\"", "\\\"")
        val t = if (h.pass.isEmpty()) "nopass" else if (h.security == 3) "SAE" else "WPA"
        return "WIFI:T:$t;S:${esc(h.ssid)};P:${esc(h.pass)};;"
    }

    /** Joins another phone's OJ-to-Dada hotspot. Returns the Network to open sockets on (null = default). */
    @SuppressLint("MissingPermission")
    suspend fun join(ctx: Context, ssid: String, pass: String?, security: Int): Network? {
        synchronized(joined) { joined[ssid] }?.let { return it }
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        if (Build.VERSION.SDK_INT >= 29) {
            val spec = WifiNetworkSpecifier.Builder().setSsid(ssid).apply {
                if (!pass.isNullOrEmpty()) {
                    if (security == 3) setWpa3Passphrase(pass) else setWpa2Passphrase(pass)
                }
            }.build()
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(spec)
                .build()
            return withTimeoutOrNull(90_000) {
                suspendCancellableCoroutine { cont ->
                    val cb = object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: Network) {
                            synchronized(joined) { joined[ssid] = network }
                            if (cont.isActive) cont.resume(network)
                        }

                        override fun onUnavailable() {
                            if (cont.isActive) cont.resume(null)
                        }

                        override fun onLost(network: Network) {
                            synchronized(joined) { joined.remove(ssid) }
                        }
                    }
                    // Keep the callback registered: unregistering would drop the Wi-Fi link.
                    synchronized(callbacks) { callbacks.add(cb) }
                    cm.requestNetwork(request, cb)
                }
            }
        } else {
            @Suppress("DEPRECATION")
            run {
                val wm = ctx.applicationContext.getSystemService(WifiManager::class.java)
                if (!wm.isWifiEnabled) wm.isWifiEnabled = true
                val conf = WifiConfiguration().apply {
                    SSID = "\"$ssid\""
                    if (pass.isNullOrEmpty()) allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                    else preSharedKey = "\"$pass\""
                }
                val id = wm.addNetwork(conf)
                if (id != -1) {
                    wm.disconnect()
                    wm.enableNetwork(id, true)
                    wm.reconnect()
                }
                repeat(40) {
                    val cur = wm.connectionInfo?.ssid?.trim('"')
                    if (cur == ssid && wm.connectionInfo.ipAddress != 0) return null
                    delay(500)
                }
            }
            return null
        }
    }

    fun releaseJoined(ctx: Context) {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        synchronized(callbacks) {
            callbacks.forEach { runCatching { cm.unregisterNetworkCallback(it) } }
            callbacks.clear()
        }
        synchronized(joined) { joined.clear() }
    }
}
