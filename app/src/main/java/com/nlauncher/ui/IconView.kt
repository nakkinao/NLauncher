package com.nlauncher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import kotlin.math.min

/** アイコン + ラベルを描画するビュー（ホーム、ドロワー、グループ内で共通） */
class IconView(context: Context) : View(context) {

    var icon: Drawable? = null
        set(value) { field = value; invalidate() }
    var label: String = ""
        set(value) { field = value; contentDescription = value; invalidate() }
    var showLabel: Boolean = true
        set(value) { field = value; invalidate() }
    var maxIconSize: Int = 60.dp
    var dimmed: Boolean = false
        set(value) { field = value; alpha = if (value) 0.4f else 1f }

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 12.sp
        textAlign = Paint.Align.CENTER
        setShadowLayer(3f.dp, 0f, 1f.dp, 0xAA000000.toInt())
    }

    init {
        selectableBorderless(context)
        isClickable = true
        isLongClickable = true
    }

    fun iconSizeFor(w: Int, h: Int): Int {
        val labelH = if (showLabel) (textPaint.fontSpacing + 4.dp) else 0f
        return min(min(w * 0.74f, (h - labelH) * 0.92f), maxIconSize.toFloat()).toInt().coerceAtLeast(8)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width
        val h = height
        val size = iconSizeFor(w, h)
        val labelH = if (showLabel) (textPaint.fontSpacing + 4.dp) else 0f
        val total = size + labelH
        val top = ((h - total) / 2f).toInt()
        val left = (w - size) / 2
        icon?.let { d ->
            d.setBounds(left, top, left + size, top + size)
            d.draw(canvas)
        }
        if (showLabel && label.isNotEmpty()) {
            val text = TextUtils.ellipsize(label, textPaint, (w - 4.dp).toFloat(), TextUtils.TruncateAt.END)
            val baseline = top + size + 4.dp - textPaint.ascent()
            canvas.drawText(text, 0, text.length, w / 2f, baseline, textPaint)
        }
    }
}
