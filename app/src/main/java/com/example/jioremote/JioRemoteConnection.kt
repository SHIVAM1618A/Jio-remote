package com.example.jioremote

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.Executors

class JioRemoteConnection(private val listener: Listener) {

    interface Listener {
        fun onConnected()
        fun onDisconnected(error: String?)
        fun onPacket(type: Int, payload: ByteArray)
    }

    companion object {
        private const val TAG = "JioRemoteConnection"
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private var socket: BluetoothSocket? = null
    private var output: OutputStream? = null
    private var input: InputStream? = null
    private val executor = Executors.newCachedThreadPool()
    @Volatile private var running = false

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        executor.execute {
            try {
                val sock = device.createRfcommSocketToServiceRecord(SPP_UUID)
                sock.connect()
                socket = sock
                output = sock.outputStream
                input = sock.inputStream
                running = true
                listener.onConnected()
                readLoop()
            } catch (e: IOException) {
                Log.e(TAG, "connect failed", e)
                listener.onDisconnected(e.message)
            }
        }
    }

    private fun readLoop() {
        val inp = input ?: return
        try {
            while (running) {
                val lenBuf = ByteArray(2)
                if (!readFully(inp, lenBuf, 2)) break
                val len = ((lenBuf[0].toInt() and 0xFF) shl 8) or (lenBuf[1].toInt() and 0xFF)

                val flagBuf = ByteArray(1)
                if (!readFully(inp, flagBuf, 1)) break

                val payload = ByteArray(len)
                if (!readFully(inp, payload, len)) break

                Log.d(
                    TAG,
                    "RX len=$len flag=${flagBuf[0]} raw=${payload.joinToString(" ") { "%02x".format(it) }}"
                )

                // flagBuf[0] == 1 would mean the payload is AES-encrypted.
                // Not handled in this MVP build — see README "Known gaps".
                if (payload.isNotEmpty()) {
                    listener.onPacket(payload[0].toInt() and 0xFF, payload)
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "read loop error", e)
        } finally {
            running = false
            listener.onDisconnected(null)
        }
    }

    private fun readFully(inp: InputStream, buf: ByteArray, len: Int): Boolean {
        var off = 0
        while (off < len) {
            val n = inp.read(buf, off, len - off)
            if (n < 0) return false
            off += n
        }
        return true
    }

    fun send(packet: ByteArray) {
        executor.execute {
            try {
                Log.d(TAG, "TX raw=${packet.joinToString(" ") { "%02x".format(it) }}")
                output?.write(packet)
                output?.flush()
            } catch (e: IOException) {
                Log.e(TAG, "send failed", e)
                listener.onDisconnected(e.message)
            }
        }
    }

    fun disconnect() {
        running = false
        try {
            socket?.close()
        } catch (_: IOException) {
        }
    }
}
