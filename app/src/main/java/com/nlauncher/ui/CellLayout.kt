package com.nlauncher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import kotlin.math.roundToInt

/** 1ページ分のグリッド。子ビューはセル単位 (x, y, w, h) で配置される */
class CellLayout(context: Context) : ViewGroup(context) {

    class LP(var x: Int, var y: Int, var w: Int, var h: Int) : ViewGroup.LayoutParams(0, 0)

    var cols = 5
        set(value) { field = value; requestLayout() }
    var rows = 6
        set(value) { field = value; requestLayout() }

    val cellWidth: Float get() = (width - paddingLeft - paddingRight).toFloat() / cols
    val cellHeight: Float get() = (height - paddingTop - paddingBottom).toFloat() / rows

    private var hint: RectF? = null
    private var hintValid = true
    private var hintMerge = false
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF }
    var showGrid = false
        set(value) { field = value; invalidate() }

    init {
        setWillNotDraw(false)
        setPadding(6.dp, 4.dp, 6.dp, 4.dp)
        clipChildren = false
        clipToPadding = false
    }

    fun addItemView(v: View, x: Int, y: Int, w: Int, h: Int) {
        (v.parent as? ViewGroup)?.removeView(v)
        addView(v, LP(x, y, w, h))
    }

    override fun checkLayoutParams(p: ViewGroup.LayoutParams?) = p is LP
    override fun generateDefaultLayoutParams(): ViewGroup.LayoutParams = LP(0, 0, 1, 1)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        val cw = (w - paddingLeft - paddingRight).toFloat() / cols
        val ch = (h - paddingTop - paddingBottom).toFloat() / rows
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            val lp = c.layoutParams as LP
            c.measure(
                MeasureSpec.makeMeasureSpec((cw * lp.w).roundToInt(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec((ch * lp.h).roundToInt(), MeasureSpec.EXACTLY),
            )
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val cw = cellWidth
        val ch = cellHeight
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            val lp = c.layoutParams as LP
            val left = (paddingLeft + lp.x * cw).roundToInt()
            val top = (paddingTop + lp.y * ch).roundToInt()
            c.layout(left, top, left + c.measuredWidth, top + c.measuredHeight)
        }
    }

    /** このビュー内のローカル座標から最も近いセルの左上を返す（w,h のアイテムを置く場合） */
    fun cellForTopLeft(localX: Float, localY: Float, w: Int, h: Int): Point {
        val cx = ((localX - paddingLeft) / cellWidth).roundToInt().coerceIn(0, (cols - w).coerceAtLeast(0))
        val cy = ((localY - paddingTop) / cellHeight).roundToInt().coerceIn(0, (rows - h).coerceAtLeast(0))
        return Point(cx, cy)
    }

    /** ローカル座標が含まれるセル */
    fun cellAt(localX: Float, localY: Float): Point {
        val cx = ((localX - paddingLeft) / cellWidth).toInt().coerceIn(0, cols - 1)
        val cy = ((localY - paddingTop) / cellHeight).toInt().coerceIn(0, rows - 1)
        return Point(cx, cy)
    }

    fun cellRect(x: Int, y: Int, w: Int, h: Int): RectF {
        val cw = cellWidth
        val ch = cellHeight
        return RectF(paddingLeft + x * cw, paddingTop + y * ch, paddingLeft + (x + w) * cw, paddingTop + (y + h) * ch)
    }

    fun setHint(x: Int, y: Int, w: Int, h: Int, valid: Boolean, merge: Boolean = false) {
        hint = cellRect(x, y, w, h)
        hintValid = valid
        hintMerge = merge
        invalidate()
    }

    fun clearHint() {
        if (hint != null) {
            hint = null
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (showGrid) {
            val cw = cellWidth
            val ch = cellHeight
            for (y in 0 until rows) for (x in 0 until cols) {
                val cx = paddingLeft + (x + 0.5f) * cw
                val cy = paddingTop + (y + 0.5f) * ch
                canvas.drawCircle(cx, cy, 2f.dp, gridPaint)
            }
        }
        hint?.let { r ->
            val inset = 3f.dp
            hintPaint.style = Paint.Style.FILL
            hintPaint.color = when {
                hintMerge -> 0x553D8BFF
                hintValid -> 0x33FFFFFF
                else -> 0x44FF5252
            }
            val rr = RectF(r.left + inset, r.top + inset, r.right - inset, r.bottom - inset)
            canvas.drawRoundRect(rr, 14f.dp, 14f.dp, hintPaint)
            hintPaint.style = Paint.Style.STROKE
            hintPaint.strokeWidth = 1.5f.dp
            hintPaint.color = if (hintMerge) 0xFF8AB4F8.toInt() else if (hintValid) 0x99FFFFFF.toInt() else 0xCCFF5252.toInt()
            canvas.drawRoundRect(rr, 14f.dp, 14f.dp, hintPaint)
        }
    }
}
