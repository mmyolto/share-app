package com.meltronics.ojtodada.core

import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

/**
 * OJ-to-Dada wire protocol (same over TCP and Bluetooth):
 *
 *   both sides:  int MAGIC, int VERSION, json HELLO
 *   sender:      json OFFER {files:[{name,size,mime}]}
 *   receiver:    json REPLY {accept}
 *   per file:    json FILE {index,name,size,mime} + exactly `size` raw bytes  ->  receiver json ACK
 *   sender:      json DONE
 *
 * Sizes are 64-bit and bytes are streamed straight to storage, so there is no file size limit.
 */
object Wire {
    const val MAGIC = 0x4F4A4444 // "OJDD"
    const val VERSION = 1
    const val BUF = 256 * 1024
    private const val MAX_JSON = 4 shl 20

    fun writeJson(out: DataOutputStream, obj: JSONObject) {
        val bytes = obj.toString().toByteArray(Charsets.UTF_8)
        out.writeInt(bytes.size)
        out.write(bytes)
        out.flush()
    }

    fun readJson(inp: DataInputStream): JSONObject {
        val n = inp.readInt()
        if (n < 0 || n > MAX_JSON) throw java.io.IOException("Bad message")
        val bytes = ByteArray(n)
        inp.readFully(bytes)
        return JSONObject(String(bytes, Charsets.UTF_8))
    }

    /** Copies exactly [size] bytes, reporting each chunk. */
    fun copy(input: InputStream, output: OutputStream, size: Long, onChunk: (Int) -> Unit) {
        val buf = ByteArray(BUF)
        var left = size
        while (left > 0) {
            val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (n < 0) throw EOFException("Connection ended early")
            output.write(buf, 0, n)
            left -= n
            onChunk(n)
        }
        output.flush()
    }
}

/** A two-way byte pipe: a TCP socket, a Bluetooth socket, etc. */
class Link(
    val input: InputStream,
    val output: OutputStream,
    private val closer: () -> Unit,
) {
    @Volatile private var closed = false
    fun close() {
        if (closed) return
        closed = true
        runCatching { closer() }
    }
}

/** Measures transfer speed and throttles UI updates. */
class Meter {
    private var windowStart = System.currentTimeMillis()
    private var windowBytes = 0L
    private var lastEmit = 0L
    var speed = 0L
        private set

    /** Returns true when the UI should be refreshed. */
    fun add(n: Int): Boolean {
        val now = System.currentTimeMillis()
        windowBytes += n
        val dt = now - windowStart
        if (dt >= 1000) {
            speed = windowBytes * 1000 / dt
            windowStart = now
            windowBytes = 0
        }
        if (now - lastEmit >= 200) {
            lastEmit = now
            return true
        }
        return false
    }
}

object Fmt {
    fun bytes(b: Long): String {
        if (b < 1024) return "$b B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var v = b / 1024.0
        var i = 0
        while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
        return if (v >= 100) "%.0f %s".format(v, units[i]) else "%.1f %s".format(v, units[i])
    }

    fun speed(bps: Long) = if (bps <= 0) "" else bytes(bps) + "/s"

    fun eta(left: Long, bps: Long): String {
        if (bps <= 0) return ""
        val s = left / bps
        return when {
            s < 60 -> "${s}s left"
            s < 3600 -> "${s / 60}m ${s % 60}s left"
            else -> "${s / 3600}h ${(s % 3600) / 60}m left"
        }
    }
}
