package com.osakaresearch.ezoprint

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.osakaresearch.ezoprint.databinding.ActivityMainBinding
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var bluetoothAdapter: BluetoothAdapter? = null
    private val pairedDevices = mutableListOf<BluetoothDevice>()
    private val printer = PrinterManager()
    private var logoBitmap: Bitmap? = null
    private var currentTemplate = TagTemplate.PRODUCT

    companion object { private const val REQ_BT = 1 }

    // ── Logo picker ──────────────────────────────────────────────────────────
    private val pickLogo = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        try {
            val stream = contentResolver.openInputStream(uri) ?: return@registerForActivityResult
            logoBitmap = BitmapFactory.decodeStream(stream)
            binding.previewView.logo = logoBitmap
            binding.btnPickLogo.text = "Logo ✓"
        } catch (e: Exception) {
            showToast("Could not load image")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bluetoothAdapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter

        setupTemplateSpinner()
        setupInputWatchers()
        setupButtons()
        requestBluetoothPermissions()
    }

    // ── Template spinner ─────────────────────────────────────────────────────
    private fun setupTemplateSpinner() {
        val names = TagTemplate.values().map { it.displayName }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerTemplate.adapter = adapter

        binding.spinnerTemplate.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) {
                currentTemplate = TagTemplate.values()[pos]
                binding.previewView.template = currentTemplate
                updateHints()
            }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        })
    }

    private fun updateHints() {
        binding.etLine1.hint = currentTemplate.hint1
        binding.etLine2.hint = currentTemplate.hint2
        binding.etLine3.hint = currentTemplate.hint3
        binding.tilLine3.visibility = if (currentTemplate == TagTemplate.NAME) android.view.View.GONE else android.view.View.VISIBLE
    }

    // ── Live preview watchers ─────────────────────────────────────────────────
    private fun setupInputWatchers() {
        fun watcher(fn: (String) -> Unit) = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { fn(s.toString()) }
        }
        binding.etLine1.addTextChangedListener(watcher { binding.previewView.line1 = it.ifBlank { currentTemplate.hint1 } })
        binding.etLine2.addTextChangedListener(watcher { binding.previewView.line2 = it.ifBlank { currentTemplate.hint2 } })
        binding.etLine3.addTextChangedListener(watcher { binding.previewView.line3 = it.ifBlank { currentTemplate.hint3 } })
    }

    // ── Buttons ──────────────────────────────────────────────────────────────
    private fun setupButtons() {
        binding.btnRefresh.setOnClickListener { loadPairedDevices() }

        binding.btnPickLogo.setOnClickListener { pickLogo.launch("image/*") }

        binding.btnClearLogo.setOnClickListener {
            logoBitmap = null
            binding.previewView.logo = null
            binding.btnPickLogo.text = "Add Logo"
        }

        binding.btnPrint.setOnClickListener { doPrint() }

        binding.btnFeed.setOnClickListener { doFeed() }
    }

    // ── Printing ─────────────────────────────────────────────────────────────
    private fun doPrint() {
        val device = selectedDevice() ?: return
        val job = printer.buildTagJob(
            currentTemplate,
            binding.etLine1.text.toString().trim(),
            binding.etLine2.text.toString().trim(),
            binding.etLine3.text.toString().trim(),
            logoBitmap
        )
        sendJob(device, job, "Printing tag...")
    }

    private fun doFeed() {
        val device = selectedDevice() ?: return
        sendJob(device, printer.buildFeedJob(10), "Feeding paper...")
    }

    private fun selectedDevice(): BluetoothDevice? {
        val idx = binding.spinnerDevices.selectedItemPosition
        if (pairedDevices.isEmpty() || idx < 0 || idx >= pairedDevices.size) {
            showToast("Select a printer first")
            return null
        }
        return pairedDevices[idx]
    }

    private fun sendJob(device: BluetoothDevice, data: ByteArray, msg: String) {
        if (!hasBtPerm()) return
        setStatus(msg)
        Thread {
            try {
                printer.connect(device)
                printer.send(data)
                Thread.sleep(400)
                printer.disconnect()
                setStatus("Done ✓")
                runOnUiThread { showToast("Success!") }
            } catch (e: IOException) {
                setStatus("Error: ${e.message}")
                runOnUiThread { showToast("Failed: ${e.message}") }
            }
        }.start()
    }

    // ── Bluetooth ─────────────────────────────────────────────────────────────
    private fun requestBluetoothPermissions() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        else arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)

        val missing = perms.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) loadPairedDevices()
        else ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQ_BT)
    }

    override fun onRequestPermissionsResult(rc: Int, p: Array<String>, g: IntArray) {
        super.onRequestPermissionsResult(rc, p, g)
        if (rc == REQ_BT && g.all { it == PackageManager.PERMISSION_GRANTED }) loadPairedDevices()
        else showToast("Bluetooth permission required")
    }

    private fun loadPairedDevices() {
        if (!hasBtPerm()) return
        pairedDevices.clear()
        pairedDevices.addAll(bluetoothAdapter?.bondedDevices ?: emptySet())
        if (pairedDevices.isEmpty()) { showToast("No paired devices found"); return }

        val names = pairedDevices.map { it.name ?: it.address }
        val a = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerDevices.adapter = a
        val ezo = pairedDevices.indexOfFirst { it.name?.contains("EZO", true) == true || it.name?.contains("58", true) == true }
        if (ezo >= 0) binding.spinnerDevices.setSelection(ezo)
        setStatus("Ready — ${pairedDevices.size} device(s) found")
    }

    private fun hasBtPerm(): Boolean {
        val p = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_CONNECT else Manifest.permission.BLUETOOTH
        return ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
    }

    private fun setStatus(msg: String) = runOnUiThread { binding.tvStatus.text = msg }
    private fun showToast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
