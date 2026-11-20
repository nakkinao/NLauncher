package com.nlauncher.ui

import android.content.Context
import android.view.MotionEvent
import android.widget.FrameLayout

/** ルートレイアウト。ドラッグ中はすべてのタッチを横取りしてハンドラに渡す */
class DragLayer(context: Context) : FrameLayout(context) {

    interface DragHandler {
        val isDragActive: Boolean
        fun onDragTouch(ev: MotionEvent)
    }

    var dragHandler: DragHandler? = null
    var lastX = 0f
        private set
    var lastY = 0f
        private set

    private var childrenCancelled = false

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        lastX = ev.x
        lastY = ev.y
        val h = dragHandler
        if (h != null && h.isDragActive) {
            if (!childrenCancelled) {
                childrenCancelled = true
                val c = MotionEvent.obtain(ev)
                c.action = MotionEvent.ACTION_CANCEL
                super.dispatchTouchEvent(c)
                c.recycle()
            }
            h.onDragTouch(ev)
            return true
        }
        childrenCancelled = false
        return super.dispatchTouchEvent(ev)
    }

    /** 子孫ビューの、このレイアウト上での座標 [left, top] */
    fun locationOf(v: android.view.View): FloatArray {
        val a = IntArray(2)
        val b = IntArray(2)
        v.getLocationInWindow(a)
        getLocationInWindow(b)
        return floatArrayOf((a[0] - b[0]).toFloat(), (a[1] - b[1]).toFloat())
    }
}
