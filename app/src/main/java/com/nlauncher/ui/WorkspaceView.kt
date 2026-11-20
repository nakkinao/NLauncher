package com.nlauncher.ui

import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.OverScroller
import kotlin.math.abs
import kotlin.math.roundToInt

/** ページ単位で横移動するコンテナ。子は CellLayout */
class WorkspaceView(context: Context) : ViewGroup(context) {

    private val scroller = OverScroller(context, DecelerateInterpolator(1.6f))
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minFling = ViewConfiguration.get(context).scaledMinimumFlingVelocity
    private var velocityTracker: VelocityTracker? = null

    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var dragging = false
    private var verticalHandled = false

    var currentPage = 0
        private set

    var onPageChanged: ((Int) -> Unit)? = null
    var onScrollProgress: ((Float) -> Unit)? = null
    var onSwipeUp: (() -> Unit)? = null
    var onSwipeDown: (() -> Unit)? = null

    val pageCount: Int get() = childCount

    fun pageAt(i: Int): CellLayout? = getChildAt(i) as? CellLayout

    val currentCellLayout: CellLayout? get() = pageAt(currentPage)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        for (i in 0 until childCount) {
            getChildAt(i).measure(
                MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY),
            )
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = r - l
        for (i in 0 until childCount) {
            getChildAt(i).layout(i * w, 0, (i + 1) * w, b - t)
        }
        if (changed && scroller.isFinished) scrollTo(currentPage * w, 0)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        scroller.forceFinished(true)
        scrollTo(currentPage * w, 0)
    }

    fun setPageImmediate(page: Int) {
        val p = page.coerceIn(0, (childCount - 1).coerceAtLeast(0))
        scroller.forceFinished(true)
        currentPage = p
        scrollTo(p * width, 0)
        onPageChanged?.invoke(p)
        reportProgress()
    }

    fun snapToPage(page: Int, duration: Int = -1) {
        val p = page.coerceIn(0, (childCount - 1).coerceAtLeast(0))
        val target = p * width
        val dx = target - scrollX
        val d = if (duration >= 0) duration else (220 + abs(dx) * 200 / width.coerceAtLeast(1)).coerceAtMost(480)
        scroller.forceFinished(true)
        scroller.startScroll(scrollX, 0, dx, 0, d)
        val changed = p != currentPage
        currentPage = p
        if (changed) onPageChanged?.invoke(p)
        postInvalidateOnAnimation()
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollTo(scroller.currX, 0)
            reportProgress()
            postInvalidateOnAnimation()
        }
    }

    private fun reportProgress() {
        if (width > 0) onScrollProgress?.invoke(scrollX.toFloat() / width)
    }

    /** タッチ位置の下にある、指定方向にスクロール可能な子ビューがあるか */
    private fun canChildScroll(v: View, x: Float, y: Float, horizontal: Boolean, direction: Int): Boolean {
        if (v !== this && v.visibility == View.VISIBLE) {
            val can = if (horizontal) v.canScrollHorizontally(direction) else v.canScrollVertically(direction)
            if (can) return true
        }
        if (v is ViewGroup) {
            for (i in v.childCount - 1 downTo 0) {
                val c = v.getChildAt(i)
                if (c.visibility != View.VISIBLE) continue
                val lx = x + v.scrollX - c.left
                val ly = y + v.scrollY - c.top
                if (lx >= 0 && ly >= 0 && lx < c.width && ly < c.height) {
                    if (canChildScroll(c, lx, ly, horizontal, direction)) return true
                }
            }
        }
        return false
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x; downY = ev.y; lastX = ev.x
                dragging = false
                verticalHandled = false
                if (!scroller.isFinished && abs(scroller.finalX - scroller.currX) > touchSlop) {
                    scroller.forceFinished(true)
                    dragging = true
                    trackVelocity(ev)
                    return true
                }
                trackVelocity(ev)
            }
            MotionEvent.ACTION_MOVE -> {
                trackVelocity(ev)
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (!dragging && abs(dx) > touchSlop && abs(dx) > abs(dy) * 1.2f) {
                    val dir = if (dx < 0) 1 else -1
                    if (!canChildScroll(this, downX, downY, true, dir)) {
                        dragging = true
                        lastX = ev.x
                        parent?.requestDisallowInterceptTouchEvent(true)
                        return true
                    }
                }
                if (!dragging && !verticalHandled && abs(dy) > touchSlop * 3 && abs(dy) > abs(dx) * 1.5f) {
                    val dir = if (dy < 0) 1 else -1
                    if (!canChildScroll(this, downX, downY, false, dir)) {
                        verticalHandled = true
                        if (dy < 0) onSwipeUp?.invoke() else onSwipeDown?.invoke()
                        return true
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                recycleVelocity()
            }
        }
        return dragging
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        trackVelocity(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x; downY = ev.y; lastX = ev.x
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (verticalHandled) return true
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (!dragging && abs(dx) > touchSlop && abs(dx) > abs(dy)) {
                    dragging = true
                    lastX = ev.x
                }
                if (!dragging && abs(dy) > touchSlop * 3 && abs(dy) > abs(dx) * 1.5f) {
                    verticalHandled = true
                    if (dy < 0) onSwipeUp?.invoke() else onSwipeDown?.invoke()
                    return true
                }
                if (dragging) {
                    var delta = (lastX - ev.x)
                    lastX = ev.x
                    val maxX = (childCount - 1).coerceAtLeast(0) * width
                    val next = scrollX + delta
                    if (next < 0 || next > maxX) delta *= 0.35f // 端では抵抗をつける
                    scrollBy(delta.roundToInt(), 0)
                    reportProgress()
                }
            }
            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    val vt = velocityTracker
                    vt?.computeCurrentVelocity(1000)
                    val vx = vt?.xVelocity ?: 0f
                    val pageF = scrollX.toFloat() / width.coerceAtLeast(1)
                    val target = when {
                        vx < -minFling * 4 -> kotlin.math.floor(pageF).toInt() + 1
                        vx > minFling * 4 -> kotlin.math.ceil(pageF).toInt() - 1
                        else -> pageF.roundToInt()
                    }
                    snapToPage(target)
                }
                dragging = false
                verticalHandled = false
                recycleVelocity()
            }
            MotionEvent.ACTION_CANCEL -> {
                if (dragging) snapToPage(currentPage)
                dragging = false
                verticalHandled = false
                recycleVelocity()
            }
        }
        return true
    }

    private fun trackVelocity(ev: MotionEvent) {
        val vt = velocityTracker ?: VelocityTracker.obtain().also { velocityTracker = it }
        // スクロールによる座標ずれを避けるため生座標で追跡
        val copy = MotionEvent.obtain(ev)
        copy.setLocation(ev.rawX, ev.rawY)
        vt.addMovement(copy)
        copy.recycle()
    }

    private fun recycleVelocity() {
        velocityTracker?.recycle()
        velocityTracker = null
    }
}
