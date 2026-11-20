package com.nlauncher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.min

/** ページのドット表示。タップでそのページへ移動 */
class PageIndicator(context: Context) : View(context) {

    var count = 1
        set(value) { field = value; invalidate() }
    var progress = 0f
        set(value) { field = value; invalidate() }
    var onPageTap: ((Int) -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spacing = 14f.dp
    private val radius = 3f.dp

    override fun onDraw(canvas: Canvas) {
        if (count <= 1) return
        val total = spacing * (count - 1)
        val start = width / 2f - total / 2f
        val cy = height / 2f
        for (i in 0 until count) {
            val d = min(1f, abs(progress - i))
            paint.color = 0xFFFFFFFF.toInt()
            paint.alpha = (255 * (1f - 0.6f * d)).toInt()
            canvas.drawCircle(start + i * spacing, cy, radius * (1.5f - 0.5f * d), paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (count <= 1) return false
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            val total = spacing * (count - 1)
            val start = width / 2f - total / 2f
            val i = ((event.x - start) / spacing + 0.5f).toInt().coerceIn(0, count - 1)
            onPageTap?.invoke(i)
        }
        return true
    }
}
