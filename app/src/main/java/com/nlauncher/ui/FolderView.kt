package com.nlauncher.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.nlauncher.data.AppInfo

/** グループを開いたときのオーバーレイ。中身は縦にスクロールできる */
class FolderView(context: Context, private val cb: Callbacks) : FrameLayout(context) {

    interface Callbacks {
        fun onFolderAppClick(app: AppInfo, view: IconView)
        fun onFolderAppMenu(app: AppInfo, view: View)
        fun onFolderReorder(from: Int, to: Int)
        fun onFolderRename(name: String)
        fun onFolderAddApps()
        fun onFolderEditIcon()
        fun onFolderMore(anchor: View)
        fun onFolderClosed()
    }

    private val card: LinearLayout
    private val title: EditText
    private val recycler: RecyclerView
    private val adapter: AppGridAdapter
    private val layoutManager = GridLayoutManager(context, 4)
    private var list = mutableListOf<AppInfo>()
    private var moved = false
    private var dragStartPos = -1
    private var dragView: View? = null

    val isOpen: Boolean get() = visibility == View.VISIBLE

    init {
        setBackgroundColor(0x88000000.toInt())
        isClickable = true
        setOnClickListener { close() }

        card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(0xF0202028.toInt(), 28)
            setPadding(12.dp, 14.dp, 12.dp, 10.dp)
            isClickable = true
            elevation = 12f.dp
        }
        title = EditText(context).apply {
            setTextColor(Color.WHITE)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            isSingleLine = true
            background = null
            imeOptions = EditorInfo.IME_ACTION_DONE
            hint = "グループ名"
            setHintTextColor(0x66FFFFFF)
            setOnEditorActionListener { _, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_DONE || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                    commitTitle()
                    true
                } else false
            }
            setOnFocusChangeListener { _, has -> if (!has) commitTitle() }
        }
        card.addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        adapter = AppGridAdapter(
            itemHeight = { 88.dp },
            onClick = { app, v -> cb.onFolderAppClick(app, v) },
            onLongClick = { _, _ -> false },
        )
        recycler = object : RecyclerView(context) {
            override fun onMeasure(widthSpec: Int, heightSpec: Int) {
                // 画面の 60% までに制限し、それ以上はスクロール
                val max = (resources.displayMetrics.heightPixels * 0.55f).toInt()
                super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST))
            }
        }.apply {
            this.layoutManager = this@FolderView.layoutManager
            this.adapter = this@FolderView.adapter
            isVerticalScrollBarEnabled = true
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(20.dp)
        }
        card.addView(recycler, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val footer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        footer.addView(footerButton("＋ アプリ") { cb.onFolderAddApps() })
        footer.addView(footerButton("アイコン") { cb.onFolderEditIcon() })
        footer.addView(footerButton("その他 ⋯") { v -> cb.onFolderMore(v) })
        card.addView(footer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = 6.dp
        })

        addView(card, LayoutParams((340).dp, LayoutParams.WRAP_CONTENT, Gravity.CENTER).apply {
            leftMargin = 16.dp; rightMargin = 16.dp
        })

        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0,
        ) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val from = vh.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from < 0 || to < 0) return false
                val item = list.removeAt(from)
                list.add(to, item)
                adapter.notifyItemMoved(from, to)
                moved = true
                return true
            }

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {}

            override fun onSelectedChanged(vh: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(vh, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && vh != null) {
                    moved = false
                    dragStartPos = vh.bindingAdapterPosition
                    dragView = vh.itemView
                    vh.itemView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                    vh.itemView.animate().scaleX(1.12f).scaleY(1.12f).setDuration(120).start()
                }
            }

            override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
                super.clearView(rv, vh)
                vh.itemView.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                val end = vh.bindingAdapterPosition
                if (moved) {
                    if (dragStartPos >= 0 && end >= 0 && dragStartPos != end) cb.onFolderReorder(dragStartPos, end)
                } else if (end >= 0 && end < list.size) {
                    cb.onFolderAppMenu(list[end], vh.itemView)
                }
                moved = false
                dragStartPos = -1
                dragView = null
            }
        }).attachToRecyclerView(recycler)

        visibility = View.GONE
    }

    private fun footerButton(text: String, onClick: (View) -> Unit): TextView = TextView(context).apply {
        this.text = text
        setTextColor(0xFFDDE3FF.toInt())
        textSize = 14f
        gravity = Gravity.CENTER
        setPadding(14.dp, 10.dp, 14.dp, 10.dp)
        selectable(context)
        setOnClickListener { onClick(it) }
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    }

    private fun commitTitle() {
        val name = title.text?.toString()?.trim().orEmpty()
        if (name.isNotEmpty()) cb.onFolderRename(name)
        hideKeyboard()
    }

    private fun hideKeyboard() {
        val imm = context.getSystemService(InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(windowToken, 0)
        title.clearFocus()
    }

    fun bind(name: String, apps: List<AppInfo>, columns: Int) {
        if (!title.hasFocus()) title.setText(name)
        list = apps.toMutableList()
        layoutManager.spanCount = columns.coerceIn(3, 5)
        adapter.submit(list)
    }

    fun open() {
        visibility = View.VISIBLE
        alpha = 0f
        card.scaleX = 0.9f; card.scaleY = 0.9f
        animate().alpha(1f).setDuration(160).start()
        card.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
    }

    fun close() {
        if (!isOpen) return
        if (title.hasFocus()) commitTitle()
        hideKeyboard()
        animate().alpha(0f).setDuration(140).withEndAction {
            visibility = View.GONE
            cb.onFolderClosed()
        }.start()
    }
}
