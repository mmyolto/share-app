package com.meltronics.ojtodada.core

import android.net.Uri

/** How to reach another device. */
sealed interface Route {
    /** Wi-Fi / hotspot / LAN. If [ssid] is set we can join that hotspot first. */
    data class Lan(
        val hosts: List<String>,
        val port: Int,
        val ssid: String? = null,
        val pass: String? = null,
        val security: Int = 1, // 1 = WPA2, 2 = WPA3 transition, 3 = WPA3 only
    ) : Route

    /** Classic Bluetooth RFCOMM. */
    data class Bt(val address: String) : Route
}

data class Peer(
    val key: String,
    val name: String,
    val route: Route,
    /** Shown in the UI: "Wi-Fi", "Bluetooth", "QR", "Hotspot" */
    val via: String,
)

/** Something the user picked to send. Size is always known (no limit, stored as Long). */
data class ShareItem(
    val uri: Uri,
    val name: String,
    val size: Long,
    val mime: String,
)

enum class Direction { SEND, RECEIVE }

enum class TStatus { WAITING, ACTIVE, DONE, FAILED, DECLINED, CANCELLED }

data class Transfer(
    val id: String,
    val peerName: String,
    val direction: Direction,
    val via: String,
    val fileCount: Int,
    val currentName: String,
    val bytesDone: Long,
    val bytesTotal: Long,
    val status: TStatus,
    val speedBps: Long = 0,
    val note: String? = null,
    val startedAt: Long = System.currentTimeMillis(),
) {
    val isLive get() = status == TStatus.WAITING || status == TStatus.ACTIVE
    val fraction get() = if (bytesTotal <= 0) 0f else (bytesDone.toDouble() / bytesTotal).toFloat().coerceIn(0f, 1f)
}

data class ReceivedFile(
    val name: String,
    val uri: String,
    val size: Long,
    val mime: String,
    val from: String,
    val time: Long,
)

data class OfferFile(val name: String, val size: Long, val mime: String)

data class IncomingOffer(
    val id: String,
    val fromName: String,
    val via: String,
    val files: List<OfferFile>,
) {
    val total get() = files.sumOf { it.size }
}

data class HotspotInfo(val ssid: String, val pass: String, val security: Int)
