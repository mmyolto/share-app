package com.meltronics.ojtodada.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import com.meltronics.ojtodada.core.Peer
import com.meltronics.ojtodada.core.QrCodec
import com.meltronics.ojtodada.core.ShareItem

enum class Screen { HOME, SEND, RECEIVE, WEB, GUIDE }

/** Simple app-wide UI state (single activity). */
object UiState {
    val screen = mutableStateOf(Screen.HOME)
    val items = mutableStateListOf<ShareItem>()
    val qrPeers = mutableStateMapOf<String, Peer>()
    val selectedPeers = mutableStateListOf<String>()

    fun addItems(list: List<ShareItem>) {
        list.forEach { n -> if (items.none { it.uri == n.uri }) items.add(n) }
    }

    fun togglePeer(key: String) {
        if (key in selectedPeers) selectedPeers.remove(key) else selectedPeers.add(key)
    }

    fun resetSend() {
        items.clear()
        selectedPeers.clear()
    }

    fun onScanned(ctx: Context, text: String) {
        val peer = QrCodec.parse(text)
        when {
            peer != null -> {
                qrPeers[peer.key] = peer
                if (peer.key !in selectedPeers) selectedPeers.add(peer.key)
                screen.value = Screen.SEND
                toast(ctx, "Added ${peer.name}" + if (items.isEmpty()) " — now choose files" else "")
            }
            text.startsWith("http://") || text.startsWith("https://") ->
                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(text))) }
            text.startsWith("WIFI:") -> toast(ctx, "That's a Wi-Fi code. Use the phone's camera app to join it.")
            else -> toast(ctx, "Not an OJ-to-Dada code")
        }
    }

    fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()

    fun permissions(): Array<String> = buildList {
        if (Build.VERSION.SDK_INT >= 33) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
            add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= 31) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }
        if (Build.VERSION.SDK_INT <= 28) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        add(Manifest.permission.CAMERA)
    }.toTypedArray()
}
