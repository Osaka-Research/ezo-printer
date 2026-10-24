package com.osakaresearch.ezoprint

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class TagPreviewView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    // 5cm x 2cm → aspect ratio 5:2
    private val TAG_W = 500f
    private val TAG_H = 200f

    var line1: String = "Product Name"
        set(v) { field = v; invalidate() }
    var line2: String = "\$0.00"
        set(v) { field = v; invalidate() }
    var line3: String = "SKU: 000"
        set(v) { field = v; invalidate() }
    var template: TagTemplate = TagTemplate.PRODUCT
        set(v) { field = v; invalidate() }
    var logo: Bitmap? = null
        set(v) { field = v; invalidate() }

    private val bgPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint().apply {
        color = Color.parseColor("#CCCCCC")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val textPaint = Paint().apply {
        isAntiAlias = true
        color = Color.BLACK
        typeface = Typeface.DEFAULT_BOLD
    }
    private val smallPaint = Paint().apply {
        isAntiAlias = true
        color = Color.BLACK
        typeface = Typeface.DEFAULT
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = (w * TAG_H / TAG_W).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val scale = w / TAG_W

        val rect = RectF(4f, 4f, w - 4f, h - 4f)
        canvas.drawRoundRect(rect, 8f, 8f, bgPaint)
        canvas.drawRoundRect(rect, 8f, 8f, borderPaint)

        val pad = 16f * scale

        when (template) {
            TagTemplate.PRODUCT -> drawProduct(canvas, w, h, scale, pad)
            TagTemplate.PRICE   -> drawPrice(canvas, w, h, scale, pad)
            TagTemplate.NAME    -> drawName(canvas, w, h, scale, pad)
            TagTemplate.CUSTOM  -> drawCustom(canvas, w, h, scale, pad)
        }
    }

    private fun drawProduct(canvas: Canvas, w: Float, h: Float, scale: Float, pad: Float) {
        var x = pad
        var y = pad

        logo?.let { bmp ->
            val logoH = h * 0.55f
            val logoW = bmp.width * logoH / bmp.height
            val dst = RectF(x, y, x + logoW.coerceAtMost(w * 0.3f), y + logoH)
            canvas.drawBitmap(bmp, null, dst, null)
            x += dst.width() + pad * 0.5f
        }

        textPaint.textSize = 30f * scale
        canvas.drawText(line1.take(22), x, y + textPaint.textSize, textPaint)

        textPaint.textSize = 26f * scale
        canvas.drawText(line2.take(18), x, y + textPaint.textSize * 2.4f, textPaint)

        smallPaint.textSize = 18f * scale
        canvas.drawText(line3.take(28), pad, h - pad * 0.8f, smallPaint)
    }

    private fun drawPrice(canvas: Canvas, w: Float, h: Float, scale: Float, pad: Float) {
        textPaint.textSize = 20f * scale
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(line1.take(24), w / 2, h * 0.32f, textPaint)

        textPaint.textSize = 38f * scale
        canvas.drawText(line2.take(12), w / 2, h * 0.72f, textPaint)

        smallPaint.textSize = 16f * scale
        smallPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(line3.take(30), w / 2, h * 0.92f, smallPaint)

        textPaint.textAlign = Paint.Align.LEFT
        smallPaint.textAlign = Paint.Align.LEFT
    }

    private fun drawName(canvas: Canvas, w: Float, h: Float, scale: Float, pad: Float) {
        textPaint.textSize = 34f * scale
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(line1.take(18), w / 2, h * 0.55f, textPaint)

        smallPaint.textSize = 18f * scale
        smallPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(line2.take(28), w / 2, h * 0.85f, smallPaint)

        textPaint.textAlign = Paint.Align.LEFT
        smallPaint.textAlign = Paint.Align.LEFT
    }

    private fun drawCustom(canvas: Canvas, w: Float, h: Float, scale: Float, pad: Float) {
        textPaint.textSize = 22f * scale
        canvas.drawText(line1.take(24), pad, h * 0.30f, textPaint)

        smallPaint.textSize = 20f * scale
        canvas.drawText(line2.take(28), pad, h * 0.58f, smallPaint)

        smallPaint.textSize = 18f * scale
        canvas.drawText(line3.take(30), pad, h * 0.85f, smallPaint)
    }
}
