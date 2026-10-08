package com.meltronics.ojtodada.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meltronics.ojtodada.core.Bt
import com.meltronics.ojtodada.core.Engine
import com.meltronics.ojtodada.core.Hotspot
import com.meltronics.ojtodada.core.Lan
import com.meltronics.ojtodada.service.TransferService
import kotlinx.coroutines.delay

@Composable
fun ReceiveScreen() {
    val ctx = LocalContext.current
    val name by Engine.deviceName.collectAsStateWithLifecycle()
    val hs by Hotspot.info.collectAsStateWithLifecycle()
    val hsError by Hotspot.error.collectAsStateWithLifecycle()
    val auto by Engine.autoAccept.collectAsStateWithLifecycle()
    val ips by produceState(Lan.localIps()) {
        while (true) { delay(2000); value = Lan.localIps() }
    }
    val qr = remember(ips, hs, name) { Engine.receiveQr() }
    val btVisible = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        Engine.restartBluetoothServer()
    }

    DisposableEffect(Unit) {
        Engine.receiveMode.value = true
        Engine.restartBluetoothServer()
        TransferService.ensure(ctx)
        onDispose { }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        TopBar("Receive") { UiState.screen.value = Screen.HOME }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Radar(110.dp) { Icon(Icons.Rounded.PhoneAndroid, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp)) }
            Text("Ready to receive", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text("as “$name”", color = MaterialTheme.colorScheme.onSurfaceVariant)
            VSpace(16.dp)

            OjCard {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    QrImage(qr, 250.dp)
                    VSpace(10.dp)
                    Text(
                        "On the sending phone: OJ-to-Dada → Send → Scan QR",
                        textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold,
                    )
                    if (ips.isEmpty() && hs == null) {
                        VSpace(8.dp)
                        Text(
                            "No Wi-Fi connected. Turn on the offline hotspot below so the other phone can join you.",
                            color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            VSpace(12.dp)

            OjCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Offline hotspot", fontWeight = FontWeight.Bold)
                        Text(
                            "No Wi-Fi router or internet? Your phone makes its own network. The QR above includes it.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = hs != null, onCheckedChange = { if (it) Hotspot.start(ctx) else Hotspot.stop() })
                }
                hs?.let {
                    VSpace(8.dp)
                    Text("Network: ${it.ssid}", fontFamily = FontFamily.Monospace)
                    Text("Password: ${it.pass}", fontFamily = FontFamily.Monospace)
                }
                hsError?.let { VSpace(6.dp); Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
            }
            VSpace(12.dp)

            OjCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Bluetooth", fontWeight = FontWeight.Bold)
                        Text(
                            if (Bt.isOn(ctx)) "Let phones find you by Bluetooth for 5 minutes" else "Bluetooth is off",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(
                        onClick = { runCatching { btVisible.launch(Bt.discoverableIntent()) } },
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Rounded.Bluetooth, null)
                        Text("Be visible")
                    }
                }
            }
            VSpace(12.dp)

            OjCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Accept automatically", fontWeight = FontWeight.Bold)
                        Text(
                            "Skip the Accept / Decline question",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = auto, onCheckedChange = { Engine.setAutoAccept(it) })
                }
            }
            VSpace(10.dp)
            Text(
                "Address: ${ips.joinToString().ifEmpty { "—" }} · port ${Engine.port}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "You can keep sending while receiving. Turn off visibility on the Home screen.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 16.dp),
            )
        }
    }
}
