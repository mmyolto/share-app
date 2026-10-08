package com.meltronics.ojtodada.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.meltronics.ojtodada.core.Direction
import com.meltronics.ojtodada.core.FileStore
import com.meltronics.ojtodada.core.Fmt
import com.meltronics.ojtodada.core.QrCodec
import com.meltronics.ojtodada.core.ShareItem
import com.meltronics.ojtodada.core.TStatus
import com.meltronics.ojtodada.core.Transfer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun TopBar(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(), modifier = modifier.padding(top = 18.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold,
    )
}

@Composable
fun OjCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) { Column(Modifier.padding(16.dp)) { content() } }
}

@Composable
fun QrImage(text: String, size: Dp = 240.dp) {
    val bmp = remember(text) { QrCodec.bitmap(text).asImageBitmap() }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(bmp, contentDescription = "QR code", modifier = Modifier.fillMaxSize(), filterQuality = FilterQuality.None)
    }
}

/** Little coloured badge with the file extension. */
@Composable
fun ExtBadge(name: String, size: Dp = 42.dp) {
    val ext = name.substringAfterLast('.', "").take(4).uppercase().ifEmpty { "FILE" }
    val color = when (ext) {
        "APK" -> OjGreen
        "EXE", "MSI" -> Color(0xFF1565C0)
        "ZIP", "RAR", "7Z" -> Color(0xFF6A1B9A)
        "MP4", "MKV", "MOV", "AVI" -> OjRed
        "JPG", "JPEG", "PNG", "HEIC", "GIF", "WEBP" -> Color(0xFFEF6C00)
        "MP3", "M4A", "WAV", "AAC" -> Color(0xFF00838F)
        else -> Color(0xFF5D4037)
    }
    Box(Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(color), contentAlignment = Alignment.Center) {
        Text(ext, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun TransferCard(t: Transfer, onCancel: () -> Unit) {
    val statusColor = when (t.status) {
        TStatus.DONE -> OjGreen
        TStatus.FAILED, TStatus.DECLINED, TStatus.CANCELLED -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    OjCard(Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .background(if (t.direction == Direction.SEND) MaterialTheme.colorScheme.primary else OjGold),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (t.direction == Direction.SEND) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                    null, tint = if (t.direction == Direction.SEND) Color.White else OjInk,
                )
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    (if (t.direction == Direction.SEND) "To " else "From ") + t.peerName,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${t.fileCount} file(s) · ${t.via} · ${t.currentName}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (t.isLive) IconButton(onClick = onCancel) { Icon(Icons.Rounded.Close, "Cancel") }
        }
        Spacer(Modifier.height(10.dp))
        if (t.status == TStatus.WAITING) {
            LinearProgressIndicator(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(6.dp)))
        } else {
            LinearProgressIndicator(
                progress = { t.fraction },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(6.dp)),
                color = if (t.status == TStatus.DONE) OjGreen else MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(6.dp))
        val label = when (t.status) {
            TStatus.WAITING -> "Waiting…"
            TStatus.ACTIVE -> "${Fmt.bytes(t.bytesDone)} of ${Fmt.bytes(t.bytesTotal)} · ${Fmt.speed(t.speedBps)} · ${Fmt.eta(t.bytesTotal - t.bytesDone, t.speedBps)}"
            TStatus.DONE -> "Done · ${Fmt.bytes(t.bytesTotal)}"
            TStatus.FAILED -> "Failed: ${t.note ?: "connection lost"}"
            TStatus.DECLINED -> "Declined"
            TStatus.CANCELLED -> "Cancelled"
        }
        Text(label, style = MaterialTheme.typography.bodySmall, color = statusColor)
    }
}

/** Animated "searching" rings used on Receive and empty states. */
@Composable
fun Radar(size: Dp, color: Color = MaterialTheme.colorScheme.primary, center: @Composable () -> Unit = {}) {
    val tr = rememberInfiniteTransition(label = "radar")
    val t by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart), label = "t")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            for (k in 0 until 3) {
                val p = (t + k / 3f) % 1f
                drawCircle(color.copy(alpha = (1f - p) * 0.55f), radius = this.size.minDimension / 2 * (0.3f + 0.7f * p), style = Stroke(width = 4f))
            }
        }
        center()
    }
}

// ---------------------------------------------------------------- app picker (share installed apps as .apk)

class AppEntry(val label: String, val path: String, val size: Long, val icon: ImageBitmap)

@Suppress("DEPRECATION")
private fun loadApps(ctx: Context): List<AppEntry> {
    val pm = ctx.packageManager
    return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        .map { it.activityInfo.applicationInfo }
        .distinctBy { it.packageName }
        .mapNotNull { ai: ApplicationInfo ->
            runCatching {
                AppEntry(
                    pm.getApplicationLabel(ai).toString(), ai.sourceDir, File(ai.sourceDir).length(),
                    pm.getApplicationIcon(ai).toBitmap(96, 96).asImageBitmap(),
                )
            }.getOrNull()
        }
        .sortedBy { it.label.lowercase() }
}

fun ownApk(ctx: Context): ShareItem {
    val f = File(ctx.applicationInfo.sourceDir)
    return ShareItem(Uri.fromFile(f), "OJ-to-Dada.apk", f.length(), FileStore.APK_MIME)
}

fun appToItem(a: AppEntry) = ShareItem(Uri.fromFile(File(a.path)), FileStore.cleanName(a.label) + ".apk", a.size, FileStore.APK_MIME)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerSheet(onDismiss: () -> Unit, onPick: (List<ShareItem>) -> Unit) {
    val ctx = LocalContext.current
    val apps by produceState<List<AppEntry>?>(null) { value = withContext(Dispatchers.IO) { loadApps(ctx) } }
    val chosen = remember { mutableStateListOf<AppEntry>() }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Share apps (.apk)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Button(enabled = chosen.isNotEmpty(), onClick = { onPick(chosen.map(::appToItem)); onDismiss() }) {
                Text(if (chosen.isEmpty()) "Add" else "Add ${chosen.size}")
            }
        }
        val list = apps
        if (list == null) {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(Modifier.fillMaxWidth().height(520.dp)) {
                items(list, key = { it.path }) { a ->
                    Row(
                        Modifier.fillMaxWidth().clickable { if (a in chosen) chosen.remove(a) else chosen.add(a) }
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(a.icon, null, Modifier.size(40.dp))
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(a.label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(Fmt.bytes(a.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Checkbox(checked = a in chosen, onCheckedChange = { if (it) chosen.add(a) else chosen.remove(a) })
                    }
                }
            }
        }
    }
}

@Composable
fun SelectedItemRow(item: ShareItem, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        ExtBadge(item.name)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(item.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(Fmt.bytes(item.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onRemove) { Text("Remove") }
    }
}

@Composable
fun Pill(text: String, color: Color) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 3.dp)) {
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun HSpace(w: Dp) = Spacer(Modifier.width(w))

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))
