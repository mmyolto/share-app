package com.meltronics.ojtodada.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.meltronics.ojtodada.MainActivity
import com.meltronics.ojtodada.core.Engine
import com.meltronics.ojtodada.core.Fmt
import com.meltronics.ojtodada.core.WebShare
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Keeps transfers running when the screen is off or the app is in the background,
 * and keeps Wi-Fi at full speed. Stops itself when nothing is happening.
 */
class TransferService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val type = if (Build.VERSION.SDK_INT >= 29)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, NOTIF_ID, build("Ready to share", "Visible to nearby devices"), type)

        @Suppress("DEPRECATION")
        wifiLock = (applicationContext.getSystemService(WifiManager::class.java))
            .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "ojdada:wifi").apply { setReferenceCounted(false); acquire() }
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ojdada:transfer").apply { setReferenceCounted(false); acquire(6 * 60 * 60 * 1000L) }

        scope.launch {
            combine(Engine.transfers, Engine.receiveMode, WebShare.running) { list, visible, web ->
                Triple(list.filter { it.isLive }, visible, web)
            }.collectLatest { (live, visible, web) ->
                if (live.isEmpty() && !visible && !web) {
                    delay(5000)
                    stopSelf()
                    return@collectLatest
                }
                val title = when {
                    live.isNotEmpty() -> "${live.size} transfer(s) in progress"
                    web -> "Sharing with computers & iPhones"
                    else -> "Ready to receive"
                }
                val text = live.firstOrNull()?.let { "${it.currentName} · ${(it.fraction * 100).toInt()}% · ${Fmt.speed(it.speedBps)}" }
                    ?: "Open OJ-to-Dada to send or receive"
                getSystemService(android.app.NotificationManager::class.java).notify(NOTIF_ID, build(title, text))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_NOT_STICKY

    override fun onDestroy() {
        scope.cancel()
        runCatching { wifiLock?.release() }
        runCatching { wakeLock?.release() }
        super.onDestroy()
    }

    private fun build(title: String, text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pi)
            .build()
    }

    companion object {
        const val CHANNEL = "ready"
        private const val NOTIF_ID = 4872

        fun ensure(ctx: Context) {
            runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, TransferService::class.java)) }
        }
    }
}
