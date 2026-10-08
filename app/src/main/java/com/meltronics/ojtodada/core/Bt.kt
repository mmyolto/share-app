package com.meltronics.ojtodada.core

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

/** Classic Bluetooth (RFCOMM). Slower than Wi-Fi but works with no network at all. */
object Bt {
    val SERVICE_UUID: UUID = UUID.fromString("6f6a2d74-6f2d-6461-6461-0000000000a1")
    private var server: BluetoothServerSocket? = null
    private var serverJob: Job? = null
    private var receiver: BroadcastReceiver? = null

    fun adapter(ctx: Context): BluetoothAdapter? = ctx.getSystemService(BluetoothManager::class.java)?.adapter

    fun canConnect(ctx: Context) = Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    fun canScan(ctx: Context) = if (Build.VERSION.SDK_INT >= 31)
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
    else ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun isOn(ctx: Context) = runCatching { adapter(ctx)?.isEnabled == true }.getOrDefault(false)

    @SuppressLint("MissingPermission")
    fun serve(ctx: Context, scope: CoroutineScope, onLink: suspend (Link, String) -> Unit) {
        if (serverJob?.isActive == true) return
        val ad = adapter(ctx) ?: return
        if (!canConnect(ctx) || !ad.isEnabled) return
        serverJob = scope.launch {
            try {
                val ss = ad.listenUsingInsecureRfcommWithServiceRecord("OJ-to-Dada", SERVICE_UUID)
                server = ss
                while (isActive) {
                    val s = ss.accept() ?: break
                    val who = runCatching { s.remoteDevice.name }.getOrNull() ?: "Bluetooth device"
                    launch { onLink(Link(s.inputStream, s.outputStream) { s.close() }, who) }
                }
            } catch (_: Exception) {
                // Bluetooth turned off or permission revoked; Engine restarts us later.
            } finally {
                runCatching { server?.close() }
                server = null
            }
        }
    }

    fun stopServer() {
        runCatching { server?.close() }
        serverJob?.cancel()
        serverJob = null
    }

    @SuppressLint("MissingPermission")
    fun connect(ctx: Context, address: String): Link {
        val ad = adapter(ctx) ?: throw java.io.IOException("No Bluetooth on this phone")
        if (!ad.isEnabled) throw java.io.IOException("Turn Bluetooth on")
        runCatching { ad.cancelDiscovery() }
        val dev = ad.getRemoteDevice(address)
        val s = dev.createInsecureRfcommSocketToServiceRecord(SERVICE_UUID)
        try {
            s.connect()
        } catch (e: Exception) {
            runCatching { s.close() }
            throw java.io.IOException("Bluetooth: ${dev.safeName()} is not ready. Open OJ-to-Dada > Receive on it.")
        }
        return Link(s.inputStream, s.outputStream) { s.close() }
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.safeName(): String = runCatching { name }.getOrNull() ?: address

    /** Paired devices + a fresh scan for nearby ones. */
    @SuppressLint("MissingPermission")
    fun startScan(ctx: Context, onFound: (Peer) -> Unit) {
        val ad = adapter(ctx) ?: return
        if (!canConnect(ctx) || !ad.isEnabled) return
        runCatching {
            ad.bondedDevices?.forEach { d ->
                onFound(Peer("bt:" + d.address, d.safeName(), Route.Bt(d.address), "Bluetooth"))
            }
        }
        if (!canScan(ctx)) return
        stopScan(ctx)
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (i.action != BluetoothDevice.ACTION_FOUND) return
                val d = IntentCompat.getParcelableExtra(i, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
                val n = runCatching { d.name }.getOrNull() ?: return // skip unnamed gadgets
                onFound(Peer("bt:" + d.address, n, Route.Bt(d.address), "Bluetooth"))
            }
        }
        receiver = r
        ContextCompat.registerReceiver(ctx, r, IntentFilter(BluetoothDevice.ACTION_FOUND), ContextCompat.RECEIVER_EXPORTED)
        runCatching { ad.startDiscovery() }
    }

    @SuppressLint("MissingPermission")
    fun stopScan(ctx: Context) {
        receiver?.let { runCatching { ctx.unregisterReceiver(it) } }
        receiver = null
        if (canScan(ctx)) runCatching { adapter(ctx)?.cancelDiscovery() }
    }

    fun discoverableIntent(seconds: Int = 300) =
        Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, seconds)
}
