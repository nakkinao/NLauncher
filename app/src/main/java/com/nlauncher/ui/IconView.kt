package com.nlauncher.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import kotlin.math.min

/** アイコン + ラベルを描画するビュー（ホーム、ドロワー、グループ内で共通）。見た目は Look のテーマに従う */
class IconView(context: Context) : View(context) {

    var icon: Drawable? = null
        set(value) { field = value; invalidate() }
    var label: String = ""
        set(value) { field = value; contentDescription = value; invalidate() }
    var showLabel: Boolean = true
        set(value) { field = value; invalidate() }
    var maxIconSize: Int = 58.dp
    /** テーマのラベル色ではなく、この色を使う（パネル・フォルダ内など） */
    var labelColorOverride: Int? = null
        set(value) { field = value; invalidate() }
    var dimmed: Boolean = false
        set(value) { field = value; alpha = if (value) 0.4f else 1f }

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private var pressScale = 1f
    private var pressAnim: ValueAnimator? = null
    private var styledVersion = -1

    init {
        isClickable = true
        isLongClickable = true
    }

    private fun applyStyle() {
        if (styledVersion == Look.version) return
        styledVersion = Look.version
        val t = Look.theme
        textPaint.textSize = t.labelSize.sp
        textPaint.typeface = Look.typeface(t.labelFont)
        if (t.labelShadow) textPaint.setShadowLayer(3f.dp, 0f, 1f.dp, 0x99000000.toInt())
        else textPaint.clearShadowLayer()
    }

    private fun labelHeight(): Float = if (showLabel) textPaint.fontSpacing + 5.dp else 0f

    fun iconSizeFor(w: Int, h: Int): Int {
        applyStyle()
        val scale = Look.theme.iconScale / 100f
        val max = maxIconSize * scale
        return min(min(w * 0.78f * scale, (h - labelHeight()) * 0.9f), max).toInt().coerceAtLeast(8)
    }

    override fun onDraw(canvas: Canvas) {
        applyStyle()
        val w = width
        val h = height
        val size = iconSizeFor(w, h)
        val total = size + labelHeight()
        val top = ((h - total) / 2f).toInt()
        val left = (w - size) / 2
        icon?.let { d ->
            canvas.save()
            canvas.scale(pressScale, pressScale, w / 2f, top + size / 2f)
            d.setBounds(left, top, left + size, top + size)
            d.draw(canvas)
            canvas.restore()
        }
        if (showLabel && label.isNotEmpty()) {
            textPaint.color = labelColorOverride ?: Look.theme.labelColor
            val text = TextUtils.ellipsize(label, textPaint, (w - 4.dp).toFloat(), TextUtils.TruncateAt.END)
            val baseline = top + size + 5.dp - textPaint.ascent()
            canvas.drawText(text, 0, text.length, w / 2f, baseline, textPaint)
        }
    }

    private fun animatePress(to: Float) {
        pressAnim?.cancel()
        pressAnim = ValueAnimator.ofFloat(pressScale, to).apply {
            duration = if (to < 1f) 90 else 260
            if (to >= 1f) interpolator = OvershootInterpolator(3f)
            addUpdateListener { pressScale = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> animatePress(0.9f)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> animatePress(1f)
        }
        return super.onTouchEvent(event)
    }
}
