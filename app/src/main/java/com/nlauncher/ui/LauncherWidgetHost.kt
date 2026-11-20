package com.nlauncher.ui

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.hypot

class LauncherWidgetHost(context: Context, hostId: Int) : AppWidgetHost(context, hostId) {
    override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView =
        LauncherWidgetHostView(context)
}

/** 長押しを検出できるウィジェットホストビュー（ウィジェット内部がタッチを消費していても検出する） */
class LauncherWidgetHostView(context: Context) : AppWidgetHostView(context) {

    var onLongPress: (() -> Unit)? = null

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val timeout = ViewConfiguration.getLongPressTimeout().toLong()
    private var downX = 0f
    private var downY = 0f
    private var pending = false
    private var performed = false

    private val longPress = Runnable {
        if (pending && isAttachedToWindow) {
            pending = false
            performed = true
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            onLongPress?.invoke()
        }
    }

    private fun track(ev: MotionEvent) {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x; downY = ev.y
                performed = false
                pending = true
                removeCallbacks(longPress)
                postDelayed(longPress, timeout)
            }
            MotionEvent.ACTION_MOVE -> {
                if (pending && hypot(ev.x - downX, ev.y - downY) > slop) cancelLongPress()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> cancelLongPress()
        }
    }

    override fun cancelLongPress() {
        super.cancelLongPress()
        pending = false
        removeCallbacks(longPress)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (performed) {
            performed = false
            return true
        }
        track(ev)
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        track(ev)
        return true
    }
}
