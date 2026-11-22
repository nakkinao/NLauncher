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
    private val spacing = 20f.dp
    private val radius = 3f.dp

    override fun onDraw(canvas: Canvas) {
        if (count <= 1) return
        val total = spacing * (count - 1)
        val start = width / 2f - total / 2f
        val cy = height / 2f
        val accent = Look.theme.accent
        for (i in 0 until count) {
            val d = min(1f, abs(progress - i))
            val active = 1f - d
            // アクティブなページは横長のピル型 + アクセントカラー
            val half = radius + active * 6f.dp
            paint.color = Look.blend(0xFFFFFFFF.toInt(), accent, active)
            paint.alpha = (255 * (0.45f + 0.55f * active)).toInt()
            val cx = start + i * spacing
            canvas.drawRoundRect(cx - half, cy - radius, cx + half, cy + radius, radius, radius, paint)
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
