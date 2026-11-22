package com.nlauncher.ui

import android.content.Context
import android.graphics.Color
import android.text.TextUtils
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nlauncher.data.AppInfo
import com.nlauncher.model.Group
import kotlin.math.max

/** ホーム画面上に置く、中身をスクロールできるグループパネル */
class GroupPanelView(
    context: Context,
    private val cellW: () -> Float,
    private val cellH: () -> Float,
    showLabels: Boolean,
    onAppClick: (AppInfo, IconView) -> Unit,
    onAppLongClick: (AppInfo, IconView) -> Boolean,
) : LinearLayout(context) {

    val header: LinearLayout
    private val title: TextView
    val iconView: ImageView
    val recycler: RecyclerView
    private val adapter: AppGridAdapter
    private val layoutManager: GridLayoutManager
    private var horizontal = false
    private val labels = showLabels

    init {
        orientation = VERTICAL
        setPadding(6.dp, 4.dp, 6.dp, 4.dp)

        header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(6.dp, 2.dp, 6.dp, 2.dp)
            selectable(context)
            isLongClickable = true
        }
        iconView = ImageView(context)
        header.addView(iconView, LayoutParams(18.dp, 18.dp).apply { marginEnd = 6.dp })
        title = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 13f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setShadowLayer(3f, 0f, 1f, 0xAA000000.toInt())
        }
        header.addView(title, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(header, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 26.dp))

        adapter = AppGridAdapter(
            itemHeight = { panelItemHeight() },
            itemWidth = { panelItemWidth() },
            showLabels = { labels },
            labelColor = { panelText() },
            onClick = onAppClick,
            onLongClick = onAppLongClick,
        )
        layoutManager = GridLayoutManager(context, 1, RecyclerView.VERTICAL, false)
        recycler = RecyclerView(context).apply {
            this.layoutManager = this@GroupPanelView.layoutManager
            this.adapter = this@GroupPanelView.adapter
            isNestedScrollingEnabled = false
            overScrollMode = OVER_SCROLL_IF_CONTENT_SCROLLS
            isVerticalFadingEdgeEnabled = true
            isHorizontalFadingEdgeEnabled = true
            setFadingEdgeLength(16.dp)
            clipToPadding = false
        }
        addView(recycler, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun panelText(): Int? {
        val t = Look.theme
        return if (Look.isLight(t.panelColor) && t.panelAlpha >= 35) Look.textOn(t.panelColor, t.panelAlpha) else null
    }

    private fun applyTheme() {
        val t = Look.theme
        background = Look.glass(t.panelColor, t.panelAlpha, t.panelRadius, t.panelBorder)
        title.setTextColor(panelText() ?: t.labelColor)
        title.typeface = Look.typeface(t.labelFont)
        if (t.labelShadow && panelText() == null) title.setShadowLayer(3f, 0f, 1f, 0xAA000000.toInt())
        else title.setShadowLayer(0f, 0f, 0f, 0)
    }

    private fun panelItemHeight(): Int {
        if (horizontal) {
            val rows = layoutManager.spanCount.coerceAtLeast(1)
            return (recycler.height / rows).coerceAtLeast(1)
        }
        return cellH().toInt().coerceAtLeast(40.dp)
    }

    private fun panelItemWidth(): Int {
        if (horizontal) return cellW().toInt().coerceAtLeast(40.dp)
        return FrameLayout.LayoutParams.MATCH_PARENT
    }

    fun bind(group: Group, apps: List<AppInfo>, icon: android.graphics.Bitmap) {
        applyTheme()
        title.text = group.name
        iconView.setImageBitmap(icon)
        horizontal = group.scrollHorizontal
        layoutManager.orientation = if (horizontal) RecyclerView.HORIZONTAL else RecyclerView.VERTICAL
        adapter.submit(apps)
        updateSpans()
    }

    private fun updateSpans() {
        val cw = cellW()
        val ch = cellH()
        if (cw <= 0 || ch <= 0 || recycler.width == 0) return
        val span = if (horizontal) {
            max(1, (recycler.height / ch).toInt())
        } else {
            max(1, (recycler.width / cw + 0.3f).toInt())
        }
        if (layoutManager.spanCount != span) layoutManager.spanCount = span
        adapter.notifyDataSetChanged()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        post { updateSpans() }
    }
}
