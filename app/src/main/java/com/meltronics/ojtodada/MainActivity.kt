package com.meltronics.ojtodada

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.meltronics.ojtodada.core.FileStore
import com.meltronics.ojtodada.ui.AppRoot
import com.meltronics.ojtodada.ui.OjTheme
import com.meltronics.ojtodada.ui.Screen
import com.meltronics.ojtodada.ui.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleShare(intent)
        setContent { OjTheme { AppRoot() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** Files shared into OJ-to-Dada from Gallery, File manager, WhatsApp, etc. */
    private fun handleShare(i: Intent?) {
        val uris: List<Uri> = when (i?.action) {
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(i, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(i, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> return
        }
        if (uris.isEmpty()) return
        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) { uris.mapNotNull { FileStore.describe(this@MainActivity, it) } }
            UiState.addItems(items)
            UiState.screen.value = Screen.SEND
        }
    }
}
