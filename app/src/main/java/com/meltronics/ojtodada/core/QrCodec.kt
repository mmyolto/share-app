package com.meltronics.ojtodada.core

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** QR codes: "ojdada://p?..." for OJ-to-Dada phones, plain URLs / Wi-Fi codes for other devices. */
object QrCodec {
    fun receiverCode(name: String, id: String, hosts: List<String>, port: Int, hs: HotspotInfo?): String =
        Uri.Builder().scheme("ojdada").authority("p")
            .appendQueryParameter("n", name)
            .appendQueryParameter("i", id)
            .appendQueryParameter("h", hosts.joinToString(","))
            .appendQueryParameter("p", port.toString())
            .apply {
                if (hs != null) {
                    appendQueryParameter("s", hs.ssid)
                    appendQueryParameter("k", hs.pass)
                    appendQueryParameter("t", hs.security.toString())
                }
            }.build().toString()

    fun parse(text: String): Peer? = runCatching {
        val u = Uri.parse(text.trim())
        if (u.scheme != "ojdada") return null
        val hosts = u.getQueryParameter("h").orEmpty().split(',').filter { it.isNotBlank() }
        val port = u.getQueryParameter("p")!!.toInt()
        val ssid = u.getQueryParameter("s")?.takeIf { it.isNotBlank() }
        Peer(
            key = "qr:" + (u.getQueryParameter("i") ?: hosts.firstOrNull() ?: text.hashCode().toString()),
            name = u.getQueryParameter("n") ?: "Phone",
            route = Route.Lan(hosts, port, ssid, u.getQueryParameter("k"), u.getQueryParameter("t")?.toIntOrNull() ?: 1),
            via = if (ssid != null) "Hotspot" else "QR",
        )
    }.getOrNull()

    fun bitmap(text: String, size: Int = 720): Bitmap {
        val hints = mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.CHARACTER_SET to "UTF-8")
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        val px = IntArray(m.width * m.height)
        for (y in 0 until m.height) for (x in 0 until m.width) {
            px[y * m.width + x] = if (m.get(x, y)) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(px, m.width, m.height, Bitmap.Config.ARGB_8888)
    }
}
