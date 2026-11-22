package com.nlauncher.ui

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nlauncher.data.AppInfo
import kotlin.math.abs

/** 全アプリ一覧（ドロワー）。すりガラスのシート */
class DrawerView(
    context: Context,
    columns: Int,
    onAppClick: (AppInfo, IconView) -> Unit,
    onAppLongClick: (AppInfo, IconView) -> Boolean,
) : FrameLayout(context) {

    val search: EditText
    val recycler: RecyclerView
    private val sheet: LinearLayout
    private val grabber: View
    private val searchWrap: LinearLayout
    private val searchIcon: TextView
    private val countView: TextView
    private val adapter: AppGridAdapter
    private val layoutManager = GridLayoutManager(context, columns)
    private var all: List<AppInfo> = emptyList()
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downY = 0f
    private var downX = 0f
    private var pulling = false
    var onCloseRequest: (() -> Unit)? = null
    /** 開閉の進み具合 0..1（ぼかしの連動用） */
    var onProgress: ((Float) -> Unit)? = null

    val isOpen: Boolean get() = visibility == View.VISIBLE

    init {
        isClickable = true
        sheet = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 10.dp, 0, 0)
        }
        grabber = View(context)
        sheet.addView(grabber, LinearLayout.LayoutParams(40.dp, 4.dp).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = 10.dp
        })

        searchWrap = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(18.dp, 0, 14.dp, 0)
        }
        searchIcon = TextView(context).apply {
            text = "⌕"
            textSize = 20f
            setPadding(0, 0, 10.dp, 2.dp)
        }
        searchWrap.addView(searchIcon)
        search = EditText(context).apply {
            hint = "アプリを検索"
            textSize = 15f
            isSingleLine = true
            background = null
            setPadding(0, 12.dp, 0, 12.dp)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) = filter()
            })
        }
        searchWrap.addView(search, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        countView = TextView(context).apply { textSize = 12f }
        searchWrap.addView(countView)
        sheet.addView(searchWrap, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(16.dp, 4.dp, 16.dp, 10.dp)
        })

        adapter = AppGridAdapter(
            itemHeight = { 96.dp },
            showLabels = { true },
            labelColor = { Look.textOn(Look.theme.drawerColor, Look.theme.drawerAlpha) },
            onClick = onAppClick,
            onLongClick = onAppLongClick,
        )
        recycler = RecyclerView(context).apply {
            this.layoutManager = this@DrawerView.layoutManager
            this.adapter = this@DrawerView.adapter
            clipToPadding = false
            setPadding(8.dp, 0, 8.dp, 24.dp)
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(24.dp)
        }
        sheet.addView(recycler, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        addView(sheet, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply { topMargin = 8.dp })
        visibility = View.GONE
        applyTheme()
    }

    fun applyTheme() {
        val t = Look.theme
        val fg = Look.textOn(t.drawerColor, t.drawerAlpha)
        val sub = Look.subTextOn(t.drawerColor, t.drawerAlpha)
        sheet.background = Look.glass(t.drawerColor, t.drawerAlpha, 30, 10, topRadiusOnly = true)
        grabber.background = roundedBg(Look.withAlpha(fg, 35), 2)
        val light = Look.isLight(t.drawerColor) && t.drawerAlpha >= 35
        searchWrap.background = roundedBg(if (light) 0x14000000 else 0x1FFFFFFF, 26, Look.withAlpha(t.accent, 30))
        searchIcon.setTextColor(t.accent)
        search.setTextColor(fg)
        search.setHintTextColor(sub)
        countView.setTextColor(sub)
        adapter.notifyDataSetChanged()
    }

    fun setColumns(c: Int) {
        layoutManager.spanCount = c
    }

    fun setApps(list: List<AppInfo>) {
        all = list
        filter()
    }

    private fun filter() {
        val q = search.text?.toString()?.trim()?.lowercase().orEmpty()
        val list = if (q.isEmpty()) all else all.filter {
            it.label.lowercase().contains(q) || it.component.packageName.lowercase().contains(q)
        }
        countView.text = "${list.size}"
        adapter.submit(list)
    }

    fun open() {
        if (isOpen) return
        visibility = View.VISIBLE
        val h = height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        sheet.translationY = h * 0.35f
        alpha = 0f
        recycler.scrollToPosition(0)
        animate().alpha(1f).setDuration(200).setUpdateListener { onProgress?.invoke(it.animatedFraction) }.start()
        sheet.animate().translationY(0f).setInterpolator(DecelerateInterpolator(2.2f)).setDuration(320).start()
    }

    fun close(animate: Boolean = true) {
        if (!isOpen) return
        hideKeyboard()
        search.setText("")
        if (!animate) {
            visibility = View.GONE
            onProgress?.invoke(0f)
            return
        }
        val h = height.toFloat()
        val startAlpha = alpha
        animate().alpha(0f).setDuration(200).setUpdateListener {
            onProgress?.invoke(startAlpha * (1f - it.animatedFraction))
        }.withEndAction {
            visibility = View.GONE
            sheet.translationY = 0f
            onProgress?.invoke(0f)
        }.start()
        sheet.animate().translationY(h * 0.3f).setDuration(220).start()
    }

    fun hideKeyboard() {
        val imm = context.getSystemService(InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(windowToken, 0)
        search.clearFocus()
    }

    // 一番上で下にスワイプすると閉じる
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downY = ev.y; downX = ev.x; pulling = false }
            MotionEvent.ACTION_MOVE -> {
                val dy = ev.y - downY
                if (dy > slop && abs(dy) > abs(ev.x - downX) && !recycler.canScrollVertically(-1)) {
                    pulling = true
                    return true
                }
            }
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (pulling) {
            val dy = (event.y - downY).coerceAtLeast(0f)
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    sheet.translationY = dy * 0.8f
                    val p = 1f - (dy / height.coerceAtLeast(1)).coerceIn(0f, 0.7f)
                    alpha = p
                    onProgress?.invoke(p)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    pulling = false
                    if (dy > 110.dp) {
                        onCloseRequest?.invoke()
                    } else {
                        sheet.animate().translationY(0f).setDuration(160).start()
                        animate().alpha(1f).setDuration(160).setUpdateListener { onProgress?.invoke(alpha) }.start()
                    }
                }
            }
            return true
        }
        return super.onTouchEvent(event)
    }
}
