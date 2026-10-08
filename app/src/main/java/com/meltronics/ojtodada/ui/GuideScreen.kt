package com.meltronics.ojtodada.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private class GuidePage(val title: String, val steps: List<String>, val art: DrawScope.(t: Float, c: ArtColors) -> Unit)

private class ArtColors(val red: Color, val gold: Color, val body: Color, val screen: Color, val ink: Color, val green: Color)

private val pages = listOf(
    GuidePage(
        "Welcome to OJ-to-Dada",
        listOf(
            "Share any file — .apk, .exe, .zip, videos — of ANY size",
            "Works with no internet, no SIM data and no router",
            "Send and receive at the same time",
        ),
    ) { t, c ->
        val l = Offset(size.width * 0.22f, size.height * 0.55f)
        val r = Offset(size.width * 0.78f, size.height * 0.55f)
        phone(l, size.height * 0.6f, c)
        phone(r, size.height * 0.6f, c)
        flyingFile(l, r, t, c.red, size.height * 0.25f)
        flyingFile(r, l, (t + 0.5f) % 1f, c.gold, size.height * 0.12f)
    },
    GuidePage(
        "Receive: show your QR",
        listOf(
            "Tap RECEIVE on the phone getting the files",
            "A QR code appears with your phone's details",
            "No Wi-Fi? Switch on Offline hotspot — the QR includes it",
        ),
    ) { t, c ->
        val ctr = Offset(size.width / 2, size.height * 0.55f)
        for (k in 0 until 3) {
            val p = (t + k / 3f) % 1f
            drawCircle(c.red.copy(alpha = (1 - p) * 0.5f), radius = size.height * (0.3f + 0.35f * p), center = ctr, style = Stroke(5f))
        }
        phone(ctr, size.height * 0.68f, c)
        val q = size.height * 0.26f * (0.6f + 0.4f * easeOutBack((t * 1.6f).coerceAtMost(1f)))
        qr(Offset(ctr.x - q / 2, ctr.y - q / 2), q, c.ink)
    },
    GuidePage(
        "Send: scan the QR",
        listOf(
            "Tap SEND and pick files, photos or apps",
            "Tap “Scan the receiver's QR code”",
            "Tap Send — the other phone taps Accept. Done!",
        ),
    ) { t, c ->
        val ctr = Offset(size.width / 2, size.height * 0.55f)
        phone(ctr, size.height * 0.75f, c)
        val q = size.height * 0.28f
        val tl = Offset(ctr.x - q / 2, ctr.y - q / 2 - size.height * 0.04f)
        qr(tl, q, c.ink)
        // viewfinder corners
        val pad = q * 0.15f
        val a = Offset(tl.x - pad, tl.y - pad)
        val s = q + pad * 2
        val len = s * 0.22f
        listOf(
            Triple(a, Offset(len, 0f), Offset(0f, len)),
            Triple(Offset(a.x + s, a.y), Offset(-len, 0f), Offset(0f, len)),
            Triple(Offset(a.x, a.y + s), Offset(len, 0f), Offset(0f, -len)),
            Triple(Offset(a.x + s, a.y + s), Offset(-len, 0f), Offset(0f, -len)),
        ).forEach { (p, h, v) ->
            drawLine(c.red, p, p + h, 6f, StrokeCap.Round)
            drawLine(c.red, p, p + v, 6f, StrokeCap.Round)
        }
        if (t < 0.75f) {
            val y = a.y + s * (0.5f - 0.5f * cos(t / 0.75f * 2 * PI.toFloat()))
            drawLine(c.red.copy(alpha = 0.85f), Offset(a.x, y), Offset(a.x + s, y), 5f)
        } else {
            val k = ((t - 0.75f) / 0.25f).coerceIn(0f, 1f)
            drawCircle(c.green, radius = q * 0.35f * easeOutBack(k), center = ctr.copy(y = tl.y + q / 2))
            if (k > 0.4f) {
                val cc = ctr.copy(y = tl.y + q / 2)
                val u = q * 0.15f
                val path = Path().apply {
                    moveTo(cc.x - u, cc.y); lineTo(cc.x - u * 0.2f, cc.y + u * 0.8f); lineTo(cc.x + u * 1.1f, cc.y - u * 0.7f)
                }
                drawPath(path, Color.White, style = Stroke(8f, cap = StrokeCap.Round))
            }
        }
    },
    GuidePage(
        "No Wi-Fi? Use the offline hotspot",
        listOf(
            "Receiver: turn on Offline hotspot, then show the QR",
            "Sender scans — the app joins the hotspot by itself",
            "Bluetooth also works (slower) — tap “Be visible”",
        ),
    ) { t, c ->
        val ctr = Offset(size.width / 2, size.height * 0.62f)
        phone(ctr, size.height * 0.55f, c)
        for (k in 0 until 3) {
            val p = (t + k / 3f) % 1f
            drawArc(
                c.gold.copy(alpha = 1 - p), startAngle = 225f, sweepAngle = 90f, useCenter = false,
                topLeft = Offset(ctr.x - size.height * (0.15f + 0.3f * p), ctr.y - size.height * 0.3f - size.height * (0.15f + 0.3f * p)),
                size = Size(size.height * (0.3f + 0.6f * p), size.height * (0.3f + 0.6f * p)),
                style = Stroke(7f, cap = StrokeCap.Round),
            )
        }
        val left = Offset(size.width * 0.15f, size.height * 0.68f)
        val right = Offset(size.width * 0.85f, size.height * 0.68f)
        phone(left, size.height * 0.38f, c)
        phone(right, size.height * 0.38f, c)
        val dash = PathEffect.dashPathEffect(floatArrayOf(14f, 12f), -t * 104f)
        drawLine(c.red, left, ctr, 4f, pathEffect = dash)
        drawLine(c.red, right, ctr, 4f, pathEffect = dash)
        // crossed-out globe = no internet
        val g = Offset(size.width * 0.85f, size.height * 0.16f)
        val gr = size.height * 0.08f
        drawCircle(c.ink.copy(alpha = 0.5f), gr, g, style = Stroke(4f))
        drawLine(c.ink.copy(alpha = 0.5f), g.copy(x = g.x - gr), g.copy(x = g.x + gr), 3f)
        drawOval(c.ink.copy(alpha = 0.5f), Offset(g.x - gr * 0.45f, g.y - gr), Size(gr * 0.9f, gr * 2), style = Stroke(3f))
        drawLine(c.red, Offset(g.x - gr, g.y + gr), Offset(g.x + gr, g.y - gr), 6f, StrokeCap.Round)
    },
    GuidePage(
        "Computers & iPhones — no app needed",
        listOf(
            "Tap “PC / iPhone” on the Home screen",
            "On the computer / iPhone, join the same Wi-Fi or hotspot",
            "Scan the QR (or type the address) and download / upload in the browser",
        ),
    ) { t, c ->
        val lp = Offset(size.width * 0.3f, size.height * 0.58f)
        laptop(lp, size.width * 0.42f, c)
        val ph = Offset(size.width * 0.8f, size.height * 0.58f)
        phone(ph, size.height * 0.55f, c)
        flyingFile(ph, lp, t, c.red, size.height * 0.18f)
        flyingFile(lp, ph, (t + 0.5f) % 1f, c.gold, -size.height * 0.05f)
    },
    GuidePage(
        "Many devices at once",
        listOf(
            "Tick several devices in the Send list — all get the files together",
            "Several people can send to you at the same time",
            "Received files are in Downloads/OJ-to-Dada. Tap an .apk to install",
        ),
    ) { t, c ->
        val src = Offset(size.width * 0.18f, size.height * 0.55f)
        phone(src, size.height * 0.5f, c)
        val targets = listOf(0.2f, 0.55f, 0.88f).map { Offset(size.width * 0.82f, size.height * it) }
        targets.forEachIndexed { i, d ->
            phone(d, size.height * 0.26f, c)
            flyingFile(src, d, (t + i * 0.15f) % 1f, if (i == 1) c.gold else c.red, size.height * 0.05f)
        }
    },
)

private fun easeOutBack(x: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1
    val y = x - 1
    return 1 + c3 * y * y * y + c1 * y * y
}

private fun DrawScope.phone(center: Offset, h: Float, c: ArtColors) {
    val w = h * 0.52f
    val tl = Offset(center.x - w / 2, center.y - h / 2)
    drawRoundRect(c.body, tl, Size(w, h), CornerRadius(w * 0.18f))
    drawRoundRect(c.screen, tl + Offset(w * 0.07f, h * 0.05f), Size(w * 0.86f, h * 0.9f), CornerRadius(w * 0.12f))
    drawRoundRect(c.body, Offset(center.x - w * 0.12f, tl.y + h * 0.065f), Size(w * 0.24f, h * 0.025f), CornerRadius(20f))
}

private fun DrawScope.laptop(center: Offset, w: Float, c: ArtColors) {
    val h = w * 0.62f
    val tl = Offset(center.x - w / 2, center.y - h / 2)
    drawRoundRect(c.body, tl, Size(w, h), CornerRadius(14f))
    drawRoundRect(c.screen, tl + Offset(w * 0.04f, h * 0.06f), Size(w * 0.92f, h * 0.86f), CornerRadius(8f))
    // browser bar
    drawRoundRect(c.red.copy(alpha = 0.8f), tl + Offset(w * 0.04f, h * 0.06f), Size(w * 0.92f, h * 0.12f), CornerRadius(8f))
    val base = Path().apply {
        moveTo(center.x - w * 0.6f, tl.y + h + h * 0.12f)
        lineTo(center.x + w * 0.6f, tl.y + h + h * 0.12f)
        lineTo(center.x + w * 0.5f, tl.y + h)
        lineTo(center.x - w * 0.5f, tl.y + h)
        close()
    }
    drawPath(base, c.body)
}

private fun DrawScope.fileIcon(center: Offset, s: Float, color: Color) {
    val w = s * 0.78f
    val f = s * 0.28f
    val l = center.x - w / 2
    val t = center.y - s / 2
    val p = Path().apply {
        moveTo(l, t); lineTo(l + w - f, t); lineTo(l + w, t + f); lineTo(l + w, t + s); lineTo(l, t + s); close()
    }
    drawPath(p, color)
    drawPath(Path().apply { moveTo(l + w - f, t); lineTo(l + w - f, t + f); lineTo(l + w, t + f); close() }, Color.White.copy(alpha = 0.6f))
    for (i in 1..3) drawLine(Color.White.copy(alpha = 0.8f), Offset(l + w * 0.18f, t + s * (0.35f + i * 0.15f)), Offset(l + w * 0.82f, t + s * (0.35f + i * 0.15f)), s * 0.05f)
}

/** File flying along an arc from a to b (t in 0..1), fading at both ends. */
private fun DrawScope.flyingFile(a: Offset, b: Offset, t: Float, color: Color, lift: Float) {
    val e = t * t * (3 - 2 * t)
    val x = a.x + (b.x - a.x) * e
    val y = a.y + (b.y - a.y) * e - lift * sin(PI.toFloat() * e)
    val alpha = when { t < 0.12f -> t / 0.12f; t > 0.88f -> (1 - t) / 0.12f; else -> 1f }
    fileIcon(Offset(x, y), size.height * 0.16f, color.copy(alpha = alpha.coerceIn(0f, 1f)))
}

private fun DrawScope.qr(tl: Offset, s: Float, ink: Color) {
    val n = 11
    val cell = s / n
    drawRect(Color.White, tl, Size(s, s))
    val bits = longArrayOf(0x1F5A3, 0x2B6C1, 0x15D97, 0x3A4E5, 0x0E7B9, 0x2D1F3, 0x19C6A, 0x34B57, 0x0F2D8, 0x27A1C, 0x1B3E6)
    for (y in 0 until n) for (x in 0 until n) {
        val inFinder = (x < 4 && y < 4) || (x > 6 && y < 4) || (x < 4 && y > 6)
        if (!inFinder && (bits[y] shr x) and 1L == 1L) drawRect(ink, tl + Offset(x * cell, y * cell), Size(cell, cell))
    }
    listOf(Offset(0f, 0f), Offset((n - 3) * cell, 0f), Offset(0f, (n - 3) * cell)).forEach { o ->
        drawRect(ink, tl + o, Size(cell * 3, cell * 3))
        drawRect(Color.White, tl + o + Offset(cell * 0.6f, cell * 0.6f), Size(cell * 1.8f, cell * 1.8f))
        drawRect(ink, tl + o + Offset(cell, cell), Size(cell, cell))
    }
}

@Composable
fun GuideScreen(onDone: () -> Unit) {
    val state = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val tr = rememberInfiniteTransition(label = "guide")
    val t by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val dark = MaterialTheme.colorScheme.background.luminanceApprox() < 0.5f
    val colors = ArtColors(
        red = OjRed, gold = OjGold,
        body = if (dark) Color(0xFF3A332D) else OjInk,
        screen = if (dark) Color(0xFF221E1B) else Color(0xFFFFF3E3),
        ink = OjInk, green = OjGreen,
    )

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDone) { Text("Skip") }
        }
        HorizontalPager(state = state, modifier = Modifier.weight(1f)) { i ->
            val page = pages[i]
            Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(1.35f).clip(RoundedCornerShape(28.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Canvas(Modifier.fillMaxSize().padding(12.dp)) { page.art(this, t, colors) }
                }
                VSpace(24.dp)
                Text(page.title, fontSize = 24.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                VSpace(14.dp)
                page.steps.forEachIndexed { n, s ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                        Box(Modifier.size(26.dp).clip(CircleShape).background(OjRed), contentAlignment = Alignment.Center) {
                            Text("${n + 1}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        HSpace(12.dp)
                        Text(s, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f)) {
                repeat(pages.size) { i ->
                    val on = state.currentPage == i
                    Box(
                        Modifier.padding(3.dp).height(8.dp).width(if (on) 22.dp else 8.dp).clip(CircleShape)
                            .background(if (on) OjRed else MaterialTheme.colorScheme.outline),
                    )
                }
            }
            val last = state.currentPage == pages.lastIndex
            Button(
                onClick = { if (last) onDone() else scope.launch { state.animateScrollToPage(state.currentPage + 1) } },
                colors = ButtonDefaults.buttonColors(containerColor = OjRed),
                shape = RoundedCornerShape(14.dp),
            ) { Text(if (last) "Let's go!" else "Next", fontWeight = FontWeight.Bold) }
        }
    }
}

private fun Color.luminanceApprox() = 0.2126f * red + 0.7152f * green + 0.0722f * blue
