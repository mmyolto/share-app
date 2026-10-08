package com.meltronics.ojtodada.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meltronics.ojtodada.core.FileStore
import com.meltronics.ojtodada.core.Hotspot
import com.meltronics.ojtodada.core.Lan
import com.meltronics.ojtodada.core.WebShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Share with Windows, Mac, Linux, iPhone, iPad — anything with a web browser. No app needed there. */
@Composable
fun WebShareScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val hs by Hotspot.info.collectAsStateWithLifecycle()
    val hsError by Hotspot.error.collectAsStateWithLifecycle()
    val shared by WebShare.shared.collectAsStateWithLifecycle()
    val running by WebShare.running.collectAsStateWithLifecycle()
    val ips by produceState(Lan.localIps()) { while (true) { delay(2000); value = Lan.localIps() } }
    var showApps by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        WebShare.start()
        onDispose { WebShare.stop() }
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        scope.launch {
            val list = withContext(Dispatchers.IO) {
                uris.mapNotNull { u ->
                    runCatching { ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    FileStore.describe(ctx, u)
                }
            }
            WebShare.add(list)
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        TopBar("PC · Mac · iPhone · iPad") { UiState.screen.value = Screen.HOME }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "No app needed on the other device. It just opens a page in its web browser — still 100% offline.",
                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionTitle("Step 1 · Same network", Modifier.fillMaxWidth())
            OjCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Offline hotspot", fontWeight = FontWeight.Bold)
                        Text(
                            if (hs == null) "Use this when there's no shared Wi-Fi" else "Join this Wi-Fi on the computer / iPhone",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = hs != null, onCheckedChange = { if (it) Hotspot.start(ctx) else Hotspot.stop() })
                }
                hs?.let { h ->
                    VSpace(12.dp)
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        QrImage(Hotspot.wifiQr(h), 190.dp)
                        VSpace(6.dp)
                        Text("iPhone / Android: scan with the Camera app to join", style = MaterialTheme.typography.bodySmall)
                        Text("Windows / Mac: pick this Wi-Fi from the list", style = MaterialTheme.typography.bodySmall)
                        VSpace(6.dp)
                        Text("Wi-Fi: ${h.ssid}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Text("Password: ${h.pass}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }
                hsError?.let { VSpace(6.dp); Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
                if (hs == null && ips.isNotEmpty()) {
                    VSpace(6.dp)
                    Text(
                        "Or connect the computer to the same Wi-Fi as this phone.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionTitle("Step 2 · Open the page", Modifier.fillMaxWidth())
            OjCard {
                val urls = remember(ips, running) { if (running) WebShare.urls() else emptyList() }
                if (urls.isEmpty()) {
                    Text("Waiting for a network… turn on the hotspot above or join a Wi-Fi.", color = MaterialTheme.colorScheme.primary)
                } else {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        QrImage(urls.first(), 210.dp)
                        VSpace(8.dp)
                        Text("Scan with the camera, or type in the browser:", style = MaterialTheme.typography.bodySmall)
                        urls.forEach {
                            Text(it, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = OjRed)
                        }
                    }
                }
            }

            SectionTitle("Step 3 · Files the browser can download", Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PickChip("Add files", Icons.Rounded.Folder, Modifier.weight(1f)) { pick.launch(arrayOf("*/*")) }
                PickChip("Add apps", Icons.Rounded.Apps, Modifier.weight(1f)) { showApps = true }
                PickChip("OJ-to-Dada app", Icons.Rounded.Android, Modifier.weight(1f)) { WebShare.add(listOf(ownApk(ctx))) }
            }
            VSpace(8.dp)
            if (shared.isNotEmpty()) {
                OjCard { shared.forEach { item -> SelectedItemRow(item) { WebShare.remove(item) } } }
            }
            Text(
                "Files uploaded from the computer arrive in Downloads/OJ-to-Dada and show on the Home screen.\n" +
                    "Tip: add the “OJ-to-Dada app” so another Android phone can install it from the browser.\n" +
                    "Keep this screen open while computers are transferring.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 16.dp),
            )
        }
    }

    if (showApps) AppPickerSheet(onDismiss = { showApps = false }) { WebShare.add(it) }
}
