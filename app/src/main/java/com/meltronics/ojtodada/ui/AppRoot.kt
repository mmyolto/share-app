package com.meltronics.ojtodada.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.meltronics.ojtodada.core.Engine
import com.meltronics.ojtodada.core.Fmt

@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    var guideFirstRun by remember { mutableStateOf(!Engine.onboarded) }
    val screen by UiState.screen

    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        Engine.restartBluetoothServer()
    }
    LaunchedEffect(Unit) { if (Engine.onboarded) perms.launch(UiState.permissions()) }

    val scanner = rememberLauncherForActivityResult(ScanContract()) { r ->
        r.contents?.let { UiState.onScanned(ctx, it) }
    }
    val scan: () -> Unit = {
        scanner.launch(
            ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt("Scan the QR code on the other device")
                .setBeepEnabled(false)
                .setOrientationLocked(true),
        )
    }

    BackHandler(enabled = !guideFirstRun && screen != Screen.HOME) { UiState.screen.value = Screen.HOME }

    if (guideFirstRun) {
        GuideScreen(onDone = {
            Engine.onboarded = true
            guideFirstRun = false
            perms.launch(UiState.permissions())
        })
    } else {
        AnimatedContent(screen, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "nav") { s ->
            when (s) {
                Screen.HOME -> HomeScreen(onScan = scan)
                Screen.SEND -> SendScreen(onScan = scan)
                Screen.RECEIVE -> ReceiveScreen()
                Screen.WEB -> WebShareScreen()
                Screen.GUIDE -> GuideScreen(onDone = { UiState.screen.value = Screen.HOME })
            }
        }
    }

    IncomingOfferDialog()
}

@Composable
private fun IncomingOfferDialog() {
    val offers by Engine.offers.collectAsStateWithLifecycle()
    val o = offers.firstOrNull() ?: return
    AlertDialog(
        onDismissRequest = {},
        title = { Text("${o.fromName} wants to send you", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "${o.files.size} file(s) · ${Fmt.bytes(o.total)} · via ${o.via}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                o.files.take(6).forEach { f ->
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        ExtBadge(f.name, 32.dp)
                        Text(
                            f.name, Modifier.weight(1f).padding(horizontal = 10.dp),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(Fmt.bytes(f.size), style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (o.files.size > 6) Text("+ ${o.files.size - 6} more", Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            Button(onClick = { Engine.answer(o.id, true) }, colors = ButtonDefaults.buttonColors(containerColor = OjGreen)) {
                Text("Accept")
            }
        },
        dismissButton = { TextButton(onClick = { Engine.answer(o.id, false) }) { Text("Decline") } },
    )
}
