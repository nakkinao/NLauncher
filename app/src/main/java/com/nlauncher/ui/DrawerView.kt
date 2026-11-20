package com.nlauncher.ui

import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nlauncher.data.AppInfo
import kotlin.math.abs

/** 全アプリ一覧（ドロワー） */
class DrawerView(
    context: Context,
    columns: Int,
    private val showLabels: () -> Boolean,
    onAppClick: (AppInfo, IconView) -> Unit,
    onAppLongClick: (AppInfo, IconView) -> Boolean,
) : FrameLayout(context) {

    val search: EditText
    val recycler: RecyclerView
    private val adapter: AppGridAdapter
    private val layoutManager = GridLayoutManager(context, columns)
    private var all: List<AppInfo> = emptyList()
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downY = 0f
    private var downX = 0f
    private var pulling = false
    var onCloseRequest: (() -> Unit)? = null

    val isOpen: Boolean get() = visibility == View.VISIBLE

    init {
        setBackgroundColor(0xF0121216.toInt())
        isClickable = true
        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        search = EditText(context).apply {
            hint = "アプリを検索"
            setHintTextColor(0x88FFFFFF.toInt())
            setTextColor(Color.WHITE)
            textSize = 16f
            isSingleLine = true
            background = roundedBg(0x22FFFFFF, 24)
            setPadding(18.dp, 10.dp, 18.dp, 10.dp)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) = filter()
            })
        }
        col.addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(16.dp, 12.dp, 16.dp, 8.dp)
        })
        adapter = AppGridAdapter(
            itemHeight = { 92.dp },
            showLabels = { true },
            onClick = onAppClick,
            onLongClick = onAppLongClick,
        )
        recycler = RecyclerView(context).apply {
            this.layoutManager = this@DrawerView.layoutManager
            this.adapter = this@DrawerView.adapter
            clipToPadding = false
            setPadding(8.dp, 0, 8.dp, 16.dp)
            isVerticalScrollBarEnabled = true
        }
        col.addView(recycler, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        addView(col, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        visibility = View.GONE
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
        adapter.submit(if (q.isEmpty()) all else all.filter {
            it.label.lowercase().contains(q) || it.component.packageName.lowercase().contains(q)
        })
    }

    fun open() {
        if (isOpen) return
        visibility = View.VISIBLE
        alpha = 0f
        translationY = height * 0.15f
        animate().alpha(1f).translationY(0f).setDuration(220).start()
        recycler.scrollToPosition(0)
    }

    fun close(animate: Boolean = true) {
        if (!isOpen) return
        hideKeyboard()
        search.setText("")
        if (!animate) {
            visibility = View.GONE
            return
        }
        animate().alpha(0f).translationY(height * 0.15f).setDuration(180).withEndAction {
            visibility = View.GONE
            translationY = 0f
        }.start()
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
                    translationY = dy * 0.6f
                    alpha = 1f - (dy / height.coerceAtLeast(1)).coerceIn(0f, 0.6f)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    pulling = false
                    if (dy > 120.dp) {
                        onCloseRequest?.invoke()
                    } else {
                        animate().translationY(0f).alpha(1f).setDuration(150).start()
                    }
                }
            }
            return true
        }
        return super.onTouchEvent(event)
    }
}
