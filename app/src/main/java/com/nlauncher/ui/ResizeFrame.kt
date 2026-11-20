package com.nlauncher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * ウィジェット・パネルのサイズ変更用オーバーレイ。
 * 四辺のハンドルをドラッグしてセル単位でサイズを変える。最小 1x1、最大はページ全体（制限なし）。
 */
class ResizeFrame(
    context: Context,
    /** セル (x,y,w,h) → このビュー上の矩形 */
    private val cellToRect: (Int, Int, Int, Int) -> RectF,
    private val cellW: Float,
    private val cellH: Float,
    private val cols: Int,
    private val rows: Int,
    startX: Int, startY: Int, startW: Int, startH: Int,
    /** 変更を適用できるか */
    private val canApply: (Int, Int, Int, Int) -> Boolean,
    private val onChange: (Int, Int, Int, Int) -> Unit,
    private val onDone: () -> Unit,
) : View(context) {

    private var cx = startX
    private var cy = startY
    private var cw = startW
    private var ch = startH
    private var rect = RectF()
    private var activeEdge = 0 // 1=L 2=T 3=R 4=B
    private var downX = 0f
    private var downY = 0f
    private var startRect = RectF()
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f.dp
        color = 0xFF8AB4F8.toInt()
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val handleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f.dp
        color = 0xFF8AB4F8.toInt()
    }
    private val dimPaint = Paint().apply { color = 0x44000000 }
    private val handleR = 9f.dp
    private val touchR = 32f.dp

    init {
        syncRect()
    }

    private fun syncRect() {
        rect = cellToRect(cx, cy, cw, ch)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), rect.top, dimPaint)
        canvas.drawRect(0f, rect.bottom, width.toFloat(), height.toFloat(), dimPaint)
        canvas.drawRect(0f, rect.top, rect.left, rect.bottom, dimPaint)
        canvas.drawRect(rect.right, rect.top, width.toFloat(), rect.bottom, dimPaint)
        canvas.drawRoundRect(rect, 12f.dp, 12f.dp, framePaint)
        val pts = arrayOf(
            floatArrayOf(rect.left, rect.centerY()), floatArrayOf(rect.centerX(), rect.top),
            floatArrayOf(rect.right, rect.centerY()), floatArrayOf(rect.centerX(), rect.bottom),
        )
        for (p in pts) {
            canvas.drawCircle(p[0], p[1], handleR, handlePaint)
            canvas.drawCircle(p[0], p[1], handleR, handleStroke)
        }
    }

    private fun edgeAt(x: Float, y: Float): Int {
        val cands = listOf(
            1 to (abs(x - rect.left) + abs(y - rect.centerY())),
            2 to (abs(x - rect.centerX()) + abs(y - rect.top)),
            3 to (abs(x - rect.right) + abs(y - rect.centerY())),
            4 to (abs(x - rect.centerX()) + abs(y - rect.bottom)),
        )
        val best = cands.minByOrNull { it.second }!!
        if (best.second < touchR * 1.4f) return best.first
        // 辺の近くでも反応させる
        if (y in rect.top..rect.bottom) {
            if (abs(x - rect.left) < touchR) return 1
            if (abs(x - rect.right) < touchR) return 3
        }
        if (x in rect.left..rect.right) {
            if (abs(y - rect.top) < touchR) return 2
            if (abs(y - rect.bottom) < touchR) return 4
        }
        return 0
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activeEdge = edgeAt(e.x, e.y)
                downX = e.x; downY = e.y
                startRect = RectF(rect)
                if (activeEdge == 0 && !rect.contains(e.x, e.y)) {
                    onDone()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeEdge == 0) return true
                val dx = e.x - downX
                val dy = e.y - downY
                val r = RectF(startRect)
                when (activeEdge) {
                    1 -> r.left = (r.left + dx).coerceAtMost(r.right - cellW * 0.6f)
                    2 -> r.top = (r.top + dy).coerceAtMost(r.bottom - cellH * 0.6f)
                    3 -> r.right = (r.right + dx).coerceAtLeast(r.left + cellW * 0.6f)
                    4 -> r.bottom = (r.bottom + dy).coerceAtLeast(r.top + cellH * 0.6f)
                }
                rect = r
                invalidate()
                // セルにスナップして即時反映
                val base = cellToRect(0, 0, 1, 1)
                var nx = cx; var ny = cy; var nw = cw; var nh = ch
                when (activeEdge) {
                    1 -> { val l = ((r.left - base.left) / cellW).roundToInt().coerceIn(0, cx + cw - 1); nw = cx + cw - l; nx = l }
                    2 -> { val t = ((r.top - base.top) / cellH).roundToInt().coerceIn(0, cy + ch - 1); nh = cy + ch - t; ny = t }
                    3 -> { nw = ((r.right - base.left) / cellW).roundToInt().coerceIn(cx + 1, cols) - cx }
                    4 -> { nh = ((r.bottom - base.top) / cellH).roundToInt().coerceIn(cy + 1, rows) - cy }
                }
                if ((nx != cx || ny != cy || nw != cw || nh != ch) && canApply(nx, ny, nw, nh)) {
                    cx = nx; cy = ny; cw = nw; ch = nh
                    onChange(cx, cy, cw, ch)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (activeEdge == 0 && rect.contains(e.x, e.y) && e.actionMasked == MotionEvent.ACTION_UP) {
                    onDone()
                    return true
                }
                activeEdge = 0
                syncRect()
            }
        }
        return true
    }
}
