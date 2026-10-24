package com.osakaresearch.ezoprint

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.osakaresearch.ezoprint.databinding.ActivityMainBinding
import java.io.IOException
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var selectedDevice: BluetoothDevice? = null
    private val pairedDevices = mutableListOf<BluetoothDevice>()

    // Standard SPP UUID used by most Bluetooth printers
    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    companion object {
        private const val REQUEST_BLUETOOTH_PERMISSIONS = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bluetoothAdapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter

        if (bluetoothAdapter == null) {
            showToast("Bluetooth not supported on this device")
            return
        }

        requestBluetoothPermissions()

        binding.btnRefresh.setOnClickListener { loadPairedDevices() }
        binding.btnPrint.setOnClickListener { printText() }
        binding.btnTestPrint.setOnClickListener { testPrint() }
    }

    private fun requestBluetoothPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            loadPairedDevices()
        } else {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQUEST_BLUETOOTH_PERMISSIONS)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS &&
            grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            loadPairedDevices()
        } else {
            showToast("Bluetooth permission required")
        }
    }

    private fun loadPairedDevices() {
        if (!hasBluetoothPermission()) return

        val bonded = bluetoothAdapter?.bondedDevices ?: emptySet()
        pairedDevices.clear()
        pairedDevices.addAll(bonded)

        if (pairedDevices.isEmpty()) {
            showToast("No paired devices found. Pair your EZO printer in Bluetooth settings first.")
            return
        }

        val names = pairedDevices.map { it.name ?: it.address }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerDevices.adapter = adapter

        binding.spinnerDevices.setSelection(
            pairedDevices.indexOfFirst { it.name?.contains("EZO", ignoreCase = true) == true }
                .takeIf { it >= 0 } ?: 0
        )

        showToast("Found ${pairedDevices.size} paired device(s)")
    }

    private fun printText() {
        val text = binding.etPrintText.text.toString().trim()
        if (text.isEmpty()) {
            showToast("Enter text to print")
            return
        }
        val idx = binding.spinnerDevices.selectedItemPosition
        if (idx < 0 || idx >= pairedDevices.size) {
            showToast("Select a printer device")
            return
        }
        selectedDevice = pairedDevices[idx]
        sendToPrinter(buildTextJob(text))
    }

    private fun testPrint() {
        val idx = binding.spinnerDevices.selectedItemPosition
        if (idx < 0 || idx >= pairedDevices.size) {
            showToast("Select a printer device first")
            return
        }
        selectedDevice = pairedDevices[idx]
        sendToPrinter(buildTestJob())
    }

    private fun sendToPrinter(data: ByteArray) {
        if (!hasBluetoothPermission()) return
        val device = selectedDevice ?: return

        setStatus("Connecting...")
        Thread {
            var socket: BluetoothSocket? = null
            try {
                socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                bluetoothAdapter?.cancelDiscovery()
                socket.connect()
                setStatus("Printing...")
                socket.outputStream.write(data)
                socket.outputStream.flush()
                Thread.sleep(500)
                setStatus("Done!")
                runOnUiThread { showToast("Printed successfully!") }
            } catch (e: IOException) {
                setStatus("Error: ${e.message}")
                runOnUiThread { showToast("Print failed: ${e.message}") }
            } finally {
                try { socket?.close() } catch (_: IOException) {}
            }
        }.start()
    }

    private fun buildTextJob(text: String): ByteArray {
        val lines = text.split("\n")
        val out = mutableListOf<Byte>()

        // ESC @ — initialize printer
        out += 0x1B; out += 0x40

        for (line in lines) {
            out.addAll(line.toByteArray(Charsets.UTF_8).toList())
            out += 0x0A  // LF
        }

        // Feed 3 lines then cut
        repeat(3) { out += 0x0A }
        out += 0x1D; out += 0x56; out += 0x41; out += 0x10  // GS V A — partial cut

        return out.toByteArray()
    }

    private fun buildTestJob(): ByteArray {
        val out = mutableListOf<Byte>()
        out += 0x1B; out += 0x40       // init
        out += 0x1B; out += 0x61; out += 0x01  // center align

        // Bold ON
        out += 0x1B; out += 0x45; out += 0x01
        out.addAll("EZO 58mm Printer\n".toByteArray().toList())
        out += 0x1B; out += 0x45; out += 0x00  // bold OFF

        out += 0x1B; out += 0x61; out += 0x00  // left align
        out.addAll("--------------------------------\n".toByteArray().toList())
        out.addAll("Test print OK\n".toByteArray().toList())
        out.addAll("Osaka Research\n".toByteArray().toList())
        out.addAll("--------------------------------\n".toByteArray().toList())

        repeat(3) { out += 0x0A }
        out += 0x1D; out += 0x56; out += 0x41; out += 0x10

        return out.toByteArray()
    }

    private fun setStatus(msg: String) = runOnUiThread { binding.tvStatus.text = msg }

    private fun showToast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun hasBluetoothPermission(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            Manifest.permission.BLUETOOTH_CONNECT else Manifest.permission.BLUETOOTH
        return ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
    }

    private operator fun MutableList<Byte>.plusAssign(b: Int) { add(b.toByte()) }
}
