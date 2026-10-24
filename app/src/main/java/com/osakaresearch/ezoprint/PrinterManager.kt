package com.osakaresearch.ezoprint

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.graphics.Bitmap
import java.io.IOException
import java.util.UUID

class PrinterManager {

    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var socket: BluetoothSocket? = null

    fun connect(device: BluetoothDevice) {
        disconnect()
        socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
        socket!!.connect()
    }

    fun send(bytes: ByteArray) {
        socket?.outputStream?.write(bytes)
        socket?.outputStream?.flush()
    }

    fun disconnect() {
        try { socket?.close() } catch (_: IOException) {}
        socket = null
    }

    // ── Print a 5cm x 2cm product tag ───────────────────────────────────────
    fun buildTagJob(
        template: TagTemplate,
        line1: String,
        line2: String,
        line3: String,
        logo: Bitmap?
    ): ByteArray {
        val b = EscPos.Builder()
        b.add(EscPos.INIT)
        b.add(EscPos.lineSpacing(24))  // tight spacing for 2cm tag

        when (template) {
            TagTemplate.PRODUCT -> buildProductTag(b, line1, line2, line3, logo)
            TagTemplate.PRICE   -> buildPriceTag(b, line1, line2, line3)
            TagTemplate.NAME    -> buildNameTag(b, line1, line2)
            TagTemplate.CUSTOM  -> buildCustomTag(b, line1, line2, line3)
        }

        b.add(EscPos.LINE_SPACING_DEFAULT)
        b.add(EscPos.feedLines(4))
        b.add(EscPos.CUT_PARTIAL)
        return b.build()
    }

    private fun buildProductTag(b: EscPos.Builder, name: String, price: String, sku: String, logo: Bitmap?) {
        logo?.let {
            b.add(EscPos.ALIGN_CENTER)
            val logoScaled = scaleLogo(it, 200, 80)
            b.add(EscPos.bitmapBytes(logoScaled))
            b.add(EscPos.LF)
        }

        b.add(EscPos.ALIGN_LEFT)
        b.add(EscPos.BOLD_ON)
        b.add(EscPos.WIDE_ON)
        b.add(name.take(24) + "\n")
        b.add(EscPos.DOUBLE_OFF)

        b.add(EscPos.BOLD_ON)
        b.add(EscPos.DOUBLE_ON)
        b.add(price.take(12) + "\n")
        b.add(EscPos.DOUBLE_OFF)
        b.add(EscPos.BOLD_OFF)

        if (sku.isNotBlank()) {
            b.add(EscPos.lineSpacing(20))
            b.add(sku.take(32) + "\n")
        }
    }

    private fun buildPriceTag(b: EscPos.Builder, name: String, price: String, note: String) {
        b.add(EscPos.ALIGN_CENTER)
        b.add(EscPos.BOLD_ON)
        b.add(name.take(24) + "\n")
        b.add(EscPos.BOLD_OFF)

        b.add(EscPos.DOUBLE_ON)
        b.add(EscPos.BOLD_ON)
        b.add(price.take(12) + "\n")
        b.add(EscPos.DOUBLE_OFF)
        b.add(EscPos.BOLD_OFF)

        if (note.isNotBlank()) b.add(note.take(32) + "\n")
        b.add(EscPos.ALIGN_LEFT)
    }

    private fun buildNameTag(b: EscPos.Builder, name: String, sub: String) {
        b.add(EscPos.ALIGN_CENTER)
        b.add(EscPos.BOLD_ON)
        b.add(EscPos.DOUBLE_ON)
        b.add(name.take(14) + "\n")
        b.add(EscPos.DOUBLE_OFF)
        b.add(EscPos.BOLD_OFF)
        if (sub.isNotBlank()) b.add(sub.take(32) + "\n")
        b.add(EscPos.ALIGN_LEFT)
    }

    private fun buildCustomTag(b: EscPos.Builder, l1: String, l2: String, l3: String) {
        b.add(EscPos.BOLD_ON)
        if (l1.isNotBlank()) b.add(l1.take(32) + "\n")
        b.add(EscPos.BOLD_OFF)
        if (l2.isNotBlank()) b.add(l2.take(32) + "\n")
        if (l3.isNotBlank()) b.add(l3.take(32) + "\n")
    }

    // ── Feed blank paper ─────────────────────────────────────────────────────
    fun buildFeedJob(lines: Int = 8): ByteArray {
        return EscPos.INIT + EscPos.feedLines(lines)
    }

    private fun scaleLogo(bmp: Bitmap, maxW: Int, maxH: Int): Bitmap {
        val ratio = minOf(maxW.toFloat() / bmp.width, maxH.toFloat() / bmp.height)
        return Bitmap.createScaledBitmap(bmp, (bmp.width * ratio).toInt(), (bmp.height * ratio).toInt(), true)
    }

    private operator fun ByteArray.plus(b: ByteArray) = this.toMutableList().also { it.addAll(b.toList()) }.toByteArray()
}
