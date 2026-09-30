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

    private fun hasPermissions(): Boolean = permissionsNeeded.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun loadPairedDevices() {
        if (!hasPermissions()) return
        val bonded = adapter.bondedDevices.toList()
        val names = bonded.map { "${it.name}\n${it.address}" }
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
        statusText.text = "Connecting to ${device.name}..."
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
            statusText.text = "Connected: ${connectedDevice?.name}"
        }
    }

    private fun setupRemoteButtons() {
        val map = mapOf(
            R.id.btnUp to KeyCode.DPAD_UP,
            R.id.btnDown to KeyCode.DPAD_DOWN,
            R.id.btnLeft to KeyCode.DPAD_LEFT,
            R.id.btnRight to KeyCode.DPAD_RIGHT,
            R.id.btnOk to KeyCode.DPAD_CENTER,
            R.id.btnBack to KeyCode.BACK,
            R.id.btnHome to KeyCode.HOME,
            R.id.btnVolUp to KeyCode.VOLUME_UP,
            R.id.btnVolDown to KeyCode.VOLUME_DOWN,
            R.id.btnMute to KeyCode.MUTE,
            R.id.btnPower to KeyCode.POWER
        )
        map.forEach { (id, code) ->
            findViewById<View>(id).setOnClickListener {
                connection?.send(BTPacket.remoteEvent(code))
            }
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

    // ---- JioRemoteConnection.Listener ----

    override fun onConnected() {
        showRemote()
    }

    override fun onDisconnected(error: String?) {
        runOnUiThread {
            Toast.makeText(this, "Disconnected: ${error ?: "connection closed"}", Toast.LENGTH_LONG).show()
        }
        showDeviceList()
    }

    override fun onPacket(type: Int, payload: ByteArray) {
        Log.d("JioRemote", "pack
