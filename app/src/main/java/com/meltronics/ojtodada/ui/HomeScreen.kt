package com.meltronics.ojtodada.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meltronics.ojtodada.core.Engine
import com.meltronics.ojtodada.core.FileStore
import com.meltronics.ojtodada.core.Fmt
import com.meltronics.ojtodada.service.TransferService
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeScreen(onScan: () -> Unit) {
    val ctx = LocalContext.current
    val name by Engine.deviceName.collectAsStateWithLifecycle()
    val transfers by Engine.transfers.collectAsStateWithLifecycle()
    val received by Engine.received.collectAsStateWithLifecycle()
    val visible by Engine.receiveMode.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).navigationBarsPadding(),
    ) {
        // Header
        Box(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                .background(Brush.linearGradient(listOf(OjRed, OjRedDeep)))
                .statusBarsPadding().padding(20.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("OJ-to-", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black)
                    Text("Dada", color = OjGold, fontSize = 32.sp, fontWeight = FontWeight.Black)
                }
                Text("Share anything · any size · no internet", color = Color.White.copy(alpha = 0.85f))
                VSpace(14.dp)
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.15f))
                        .clickable { renaming = true }.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (visible) Color(0xFF7CFC9A) else Color.White.copy(alpha = 0.5f)))
                    HSpace(8.dp)
                    Text(name, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    HSpace(6.dp)
                    Icon(Icons.Rounded.Edit, "Rename", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }

        Column(Modifier.padding(horizontal = 16.dp)) {
            VSpace(16.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigAction("Send", "Files, apps, anything", Icons.Rounded.ArrowUpward, OjRed, Color.White, Modifier.weight(1f)) {
                    UiState.screen.value = Screen.SEND
                }
                BigAction("Receive", "Show my QR code", Icons.Rounded.ArrowDownward, OjGold, OjInk, Modifier.weight(1f)) {
                    UiState.screen.value = Screen.RECEIVE
                }
            }
            VSpace(12.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallAction("Scan QR", Icons.Rounded.QrCodeScanner, Modifier.weight(1f), onScan)
                SmallAction("PC / iPhone", Icons.Rounded.Computer, Modifier.weight(1f)) { UiState.screen.value = Screen.WEB }
                SmallAction("How to use", Icons.Rounded.PlayCircle, Modifier.weight(1f)) { UiState.screen.value = Screen.GUIDE }
            }
            VSpace(12.dp)
            OjCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Visible to nearby devices", fontWeight = FontWeight.Bold)
                        Text(
                            if (visible) "Others can send to you any time, even with the screen off"
                            else "Turn on to receive in the background",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = visible, onCheckedChange = {
                        Engine.receiveMode.value = it
                        if (it) { Engine.restartBluetoothServer(); TransferService.ensure(ctx) }
                    })
                }
            }

            if (transfers.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Transfers", Modifier.weight(1f))
                    if (transfers.any { !it.isLive }) TextButton(onClick = { Engine.clearFinished() }) { Text("Clear finished") }
                }
                transfers.take(40).forEach { t -> TransferCard(t) { Engine.cancel(t.id) } }
            }

            SectionTitle("Received · Downloads/OJ-to-Dada")
            if (received.isEmpty()) {
                Text(
                    "Nothing received yet. Tap Receive and let the other person scan your QR code.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 24.dp),
                )
            } else {
                val df = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
                OjCard(Modifier.padding(bottom = 24.dp)) {
                    received.take(60).forEach { f ->
                        Row(
                            Modifier.fillMaxWidth().clickable { FileStore.open(ctx, f) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ExtBadge(f.name)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(f.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${Fmt.bytes(f.size)} · from ${f.from} · ${df.format(Date(f.time))}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (f.mime == FileStore.APK_MIME) Pill("Install", OjGreen) else Pill("Open", MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }

    if (renaming) {
        var text by remember { mutableStateOf(name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Device name") },
            text = {
                Column {
                    Text("This is the name other people see.", style = MaterialTheme.typography.bodySmall)
                    VSpace(8.dp)
                    OutlinedTextField(value = text, onValueChange = { text = it.take(40) }, singleLine = true)
                }
            },
            confirmButton = { TextButton(onClick = { Engine.rename(text); renaming = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BigAction(
    title: String, sub: String, icon: ImageVector, bg: Color, fg: Color,
    modifier: Modifier, onClick: () -> Unit,
) {
    Column(
        modifier.height(150.dp).clip(RoundedCornerShape(24.dp)).background(bg).clickable(onClick = onClick).padding(18.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(fg.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(28.dp))
        }
        Column {
            Text(title, color = fg, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text(sub, color = fg.copy(alpha = 0.8f), fontSize = 13.sp)
        }
    }
}

@Composable
private fun SmallAction(title: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        VSpace(6.dp)
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
