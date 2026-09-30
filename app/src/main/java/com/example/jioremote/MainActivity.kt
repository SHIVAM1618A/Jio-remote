package com.example.jioremote

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream

object BTPacketType {
    const val CONNECTION = 1
    const val CONNECTION_ACK = 2
    const val REMOTE_EVENT = 7
    const val VOLUME_UPDATE = 13
    const val SOFT_KEYBOARD = 18
    const val PAIRING_REQUEST_PIN = 57
}

object KeyCode {
    const val HOME = 3
    const val BACK = 4
    const val DPAD_UP = 19
    const val DPAD_DOWN = 20
    const val DPAD_LEFT = 21
    const val DPAD_RIGHT = 22
    const val DPAD_CENTER = 23
    const val VOLUME_UP = 24
    const val VOLUME_DOWN = 25
    const val POWER = 26
    const val MUTE = 164
}

object BTPacket {

    private fun frame(payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        val len = payload.size
        out.write((len shr 8) and 0xFF)
        out.write(len and 0xFF)
        out.write(0)
        out.write(payload)
        return out.toByteArray()
    }

    fun remoteEvent(keyCode: Int, action: Int = 0): ByteArray {
        val payload = ByteArrayOutputStream()
        payload.write(BTPacketType.REMOTE_EVENT)
        payload.write((keyCode shr 8) and 0xFF)
        payload.write(keyCode and 0xFF)
        payload.write((action shr 24) and 0xFF)
        payload.write((action shr 16) and 0xFF)
        payload.write((action shr 8) and 0xFF)
        payload.write(action and 0xFF)
        return frame(payload.toByteArray())
    }

    fun pairingPin(event: Int, text: String): ByteArray {
        val payload = ByteArrayOutputStream()
        payload.write(BTPacketType.PAIRING_REQUEST_PIN)
        payload.write((event shr 24) and 0xFF)
        payload.write((event shr 16) and 0xFF)
        payload.write((event shr 8) and 0xFF)
        payload.write(event and 0xFF)
        payload.write(text.toByteArray(Charsets.UTF_8))
        return frame(payload.toByteArray())
    }

    fun softKeyboard(text: String): ByteArray {
        val payload = ByteArrayOutputStream()
        payload.write(BTPacketType.SOFT_KEYBOARD)
        payload.write(text.toByteArray(Charsets.UTF_8))
        return frame(payload.toByteArray())
    }
}

class MainActivity : AppCompatActivity(), JioRemoteConnection.Listener {

    private lateinit var adapter: BluetoothAdapter
    private var connection: JioRemoteConnection? = null
    private var connectedDevice: BluetoothDevice? = null

    private lateinit var deviceListView: ListView
    private lateinit var deviceLayout: View
    private lateinit var remoteLayout: View
    private lateinit var statusText: TextView

    private val permissionsNeeded: Array<String> = mutableListOf<String>().apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }.toTypedArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        deviceLayout = findViewById(R.id.deviceLayout)
        remoteLayout = findViewById(R.id.remoteLayout)
        deviceListView = findViewById(R.id.deviceListView)
        statusText = findViewById(R.id.statusText)

        val btManager = getSystemService(BluetoothManager::class.java)
        adapter = btManager.adapter

        findViewById<View>(R.id.btnRefresh).setOnClickListener { loadPairedDevices() }
        findViewById<View>(R.id.btnDisconnect).setOnClickListener { disconnectDevice() }

        setupRemoteButtons()

        if (!hasPermissions()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded, 1)
        } else {
            loadPairedDevices()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (hasPermissions()) {
            loadPairedDevices()
        } else {
            Toast.makeText(this, "Bluetooth permission chahiye", Toast.LENGTH_LONG).show()
        }
    }

    private fun hasPermissions(): Boolean {
        for (p in permissionsNeeded) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun loadPairedDevices() {
        if (!hasPermissions()) return
        val bonded = adapter.bondedDevices.toList()
        val names = ArrayList<String>()
        for (d in bonded) {
            names.add(d.name + "\n" + d.address)
        }
        deviceListView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, names)
        deviceListView.setOnItemClickListener { _, _, position, _ ->
            connectTo(bonded[position])
        }
        if (bonded.isEmpty()) {
            Toast.makeText(
                this,
                "Koi paired device nahi mila. Pehle STB ko Android Settings > Bluetooth se pair karo.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun connectTo(device: BluetoothDevice) {
        statusText.text = "Connecting to " + device.name
        connection = JioRemoteConnection(this)
        connection?.connect(device)
        connectedDevice = device
    }

    private fun disconnectDevice() {
        connection?.disconnect()
        connection = null
        showDeviceList()
    }

    private fun showDeviceList() {
        runOnUiThread {
            deviceLayout.visibility = View.VISIBLE
            remoteLayout.visibility = View.GONE
            statusText.text = "Disconnected"
        }
    }

    private fun showRemote() {
        runOnUiThread {
            deviceLayout.visibility = View.GONE
            remoteLayout.visibility = View.VISIBLE
            statusText.text = "Connected: " + (connectedDevice?.name ?: "")
        }
    }

    private fun setupRemoteButtons() {
        bindKey(R.id.btnUp, KeyCode.DPAD_UP)
        bindKey(R.id.btnDown, KeyCode.DPAD_DOWN)
        bindKey(R.id.btnLeft, KeyCode.DPAD_LEFT)
        bindKey(R.id.btnRight, KeyCode.DPAD_RIGHT)
        bindKey(R.id.btnOk, KeyCode.DPAD_CENTER)
        bindKey(R.id.btnBack, KeyCode.BACK)
        bindKey(R.id.btnHome, KeyCode.HOME)
        bindKey(R.id.btnVolUp, KeyCode.VOLUME_UP)
        bindKey(R.id.btnVolDown, KeyCode.VOLUME_DOWN)
        bindKey(R.id.btnMute, KeyCode.MUTE)
        bindKey(R.id.btnPower, KeyCode.POWER)
    }

    private fun bindKey(viewId: Int, code: Int) {
        findViewById<View>(viewId).setOnClickListener {
            connection?.send(BTPacket.remoteEvent(code))
        }
    }

    private fun promptPin() {
        runOnUiThread {
            val input = EditText(this)
            input.inputType = InputType.TYPE_CLASS_NUMBER
            AlertDialog.Builder(this)
                .setTitle("TV par dikh raha 4-digit code daalo")
                .setView(input)
                .setPositiveButton("Submit") { _, _ ->
                    val pin = input.text.toString()
                    connection?.send(BTPacket.pairingPin(2, pin))
                }
                .setCancelable(false)
                .show()
        }
    }

    override fun onConnected() {
        showRemote()
    }

    override fun onDisconnected(error: String?) {
        val message = "Disconnected: " + (error ?: "connection closed")
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
        showDeviceList()
    }

    override fun onPacket(type: Int, payload: ByteArray) {
        val hexBuilder = StringBuilder()
        for (b in payload) {
            hexBuilder.append(String.format("%02x ", b))
        }
        Log.d("JioRemote", "packet type=" + type + " raw=" + hexBuilder.toString())
        if (type == BTPacketType.PAIRING_REQUEST_PIN) {
            promptPin()
        }
    }
}
