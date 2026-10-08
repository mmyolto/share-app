package com.meltronics.ojtodada.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meltronics.ojtodada.core.Engine
import com.meltronics.ojtodada.core.FileStore
import com.meltronics.ojtodada.core.Fmt
import com.meltronics.ojtodada.core.Peer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SendScreen(onScan: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var showApps by remember { mutableStateOf(false) }

    fun addUris(uris: List<Uri>) {
        scope.launch {
            val list = withContext(Dispatchers.IO) {
                uris.mapNotNull { u ->
                    runCatching { ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    FileStore.describe(ctx, u)
                }
            }
            UiState.addItems(list)
        }
    }

    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { addUris(it) }
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { addUris(it) }

    DisposableEffect(Unit) {
        Engine.startDiscovery()
        Engine.scanBluetooth()
        onDispose { Engine.stopDiscovery() }
    }

    val lan by Engine.nearbyLan.collectAsStateWithLifecycle()
    val bt by Engine.nearbyBt.collectAsStateWithLifecycle()
    val peers: List<Peer> = (UiState.qrPeers.values.toList() + lan.values + bt.values).distinctBy { it.key }
    val items = UiState.items
    val chosenPeers = peers.filter { it.key in UiState.selectedPeers }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        TopBar("Send") { UiState.screen.value = Screen.HOME }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SectionTitle("1 · What to send")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PickChip("Files", Icons.Rounded.Folder, Modifier.weight(1f)) { pickFiles.launch(arrayOf("*/*")) }
                PickChip("Photos", Icons.Rounded.PhotoLibrary, Modifier.weight(1f)) {
                    pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                }
                PickChip("Apps", Icons.Rounded.Apps, Modifier.weight(1f)) { showApps = true }
                PickChip("This app", Icons.Rounded.Android, Modifier.weight(1f)) { UiState.addItems(listOf(ownApk(ctx))) }
            }
            VSpace(10.dp)
            if (items.isEmpty()) {
                Text(
                    "Pick files of any type and size: .apk, .exe, .zip, videos, documents…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                OjCard {
                    Text("${items.size} item(s) · ${Fmt.bytes(items.sumOf { it.size })}", fontWeight = FontWeight.Bold)
                    items.toList().forEach { item -> SelectedItemRow(item) { UiState.items.remove(item) } }
                }
            }

            SectionTitle("2 · Send to (pick one or many)")
            OutlinedButton(onClick = onScan, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Rounded.QrCodeScanner, null)
                HSpace(8.dp)
                Text("Scan the receiver's QR code")
            }
            VSpace(10.dp)
            if (peers.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Radar(120.dp) { Icon(Icons.Rounded.PhoneAndroid, null, tint = MaterialTheme.colorScheme.primary) }
                    Text(
                        "Looking for nearby phones…\nOn the other phone open OJ-to-Dada → Receive, then scan its QR.",
                        textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                OjCard {
                    peers.forEach { p ->
                        val icon = when (p.via) {
                            "Bluetooth" -> Icons.Rounded.Bluetooth
                            "Hotspot" -> Icons.Rounded.WifiTethering
                            else -> Icons.Rounded.Wifi
                        }
                        Row(
                            Modifier.fillMaxWidth().clickable { UiState.togglePeer(p.key) }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(p.name, fontWeight = FontWeight.SemiBold)
                                Text(p.via, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Checkbox(checked = p.key in UiState.selectedPeers, onCheckedChange = { UiState.togglePeer(p.key) })
                        }
                    }
                }
                Text(
                    "Bluetooth works without any Wi-Fi but is slower. Wi-Fi / hotspot is fastest.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            VSpace(24.dp)
        }

        Button(
            onClick = {
                Engine.send(items.toList(), chosenPeers)
                UiState.toast(ctx, "Sending to ${chosenPeers.joinToString { it.name }}")
                UiState.resetSend()
                UiState.screen.value = Screen.HOME
            },
            enabled = items.isNotEmpty() && chosenPeers.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = OjRed),
        ) {
            Text(
                when {
                    items.isEmpty() -> "Choose files first"
                    chosenPeers.isEmpty() -> "Choose a device"
                    else -> "Send ${items.size} item(s) to ${chosenPeers.size} device(s)"
                },
                fontWeight = FontWeight.Bold,
            )
        }
    }

    if (showApps) AppPickerSheet(onDismiss = { showApps = false }) { UiState.addItems(it) }
}

@Composable
fun PickChip(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        VSpace(4.dp)
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}
