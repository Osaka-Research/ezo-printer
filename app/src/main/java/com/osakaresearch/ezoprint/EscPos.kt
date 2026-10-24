package com.osakaresearch.ezoprint

import android.graphics.Bitmap

object EscPos {

    // ── Init / Cut ──────────────────────────────────────────────────────────
    val INIT        = byteArrayOf(0x1B, 0x40)
    val CUT_PARTIAL = byteArrayOf(0x1D, 0x56, 0x41, 0x10)
    val LF          = byteArrayOf(0x0A)

    // ── Alignment ───────────────────────────────────────────────────────────
    val ALIGN_LEFT   = byteArrayOf(0x1B, 0x61, 0x00)
    val ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)
    val ALIGN_RIGHT  = byteArrayOf(0x1B, 0x61, 0x02)

    // ── Text style ──────────────────────────────────────────────────────────
    val BOLD_ON      = byteArrayOf(0x1B, 0x45, 0x01)
    val BOLD_OFF     = byteArrayOf(0x1B, 0x45, 0x00)
    val DOUBLE_ON    = byteArrayOf(0x1D, 0x21, 0x11)   // 2x width + 2x height
    val DOUBLE_OFF   = byteArrayOf(0x1D, 0x21, 0x00)
    val WIDE_OFF     = byteArrayOf(0x1D, 0x21, 0x00)
    val WIDE_ON      = byteArrayOf(0x1D, 0x21, 0x10)   // 2x width only
    val TALL_ON      = byteArrayOf(0x1D, 0x21, 0x01)   // 2x height only

    // ── Line spacing ────────────────────────────────────────────────────────
    fun lineSpacing(n: Int) = byteArrayOf(0x1B, 0x33, n.toByte())
    val LINE_SPACING_DEFAULT = byteArrayOf(0x1B, 0x32)

    // ── Feed ────────────────────────────────────────────────────────────────
    fun feedLines(n: Int) = byteArrayOf(0x1B, 0x64, n.toByte())

    // ── Bitmap printing (GS v 0 raster) ─────────────────────────────────────
    fun bitmapBytes(bmp: Bitmap): ByteArray {
        val scaledW = minOf(bmp.width, 384)
        val scaledH = (bmp.height * scaledW.toFloat() / bmp.width).toInt()
        val scaled = Bitmap.createScaledBitmap(bmp, scaledW, scaledH, true)

        val byteW = (scaledW + 7) / 8
        val out = mutableListOf<Byte>()

        // GS v 0 — raster bit image
        out += 0x1D; out += 0x76; out += 0x30; out += 0x00
        out += (byteW and 0xFF).toByte()
        out += ((byteW shr 8) and 0xFF).toByte()
        out += (scaledH and 0xFF).toByte()
        out += ((scaledH shr 8) and 0xFF).toByte()

        for (y in 0 until scaledH) {
            for (bx in 0 until byteW) {
                var byte = 0
                for (bit in 0 until 8) {
                    val x = bx * 8 + bit
                    if (x < scaledW) {
                        val pixel = scaled.getPixel(x, y)
                        val r = (pixel shr 16) and 0xFF
                        val g = (pixel shr 8) and 0xFF
                        val b = pixel and 0xFF
                        val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                        if (gray < 128) byte = byte or (0x80 shr bit)
                    }
                }
                out += byte.toByte()
            }
        }
        return out.toByteArray()
    }

    // ── Builder ─────────────────────────────────────────────────────────────
    class Builder {
        private val buf = mutableListOf<Byte>()

        fun add(bytes: ByteArray) = apply { buf.addAll(bytes.toList()) }
        fun add(s: String) = apply { buf.addAll(s.toByteArray(Charsets.UTF_8).toList()) }

        fun build(): ByteArray = buf.toByteArray()
    }

    private operator fun MutableList<Byte>.plusAssign(b: Int) { add(b.toByte()) }
    private operator fun MutableList<Byte>.plusAssign(b: Byte) { add(b) }
}
