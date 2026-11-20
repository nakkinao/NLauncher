package com.nlauncher.ui

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.nlauncher.data.AppInfo

/** アプリアイコンのグリッド（ドロワー・グループ・パネル共通） */
class AppGridAdapter(
    private val itemHeight: () -> Int,
    private val itemWidth: () -> Int = { ViewGroup.LayoutParams.MATCH_PARENT },
    private val showLabels: () -> Boolean = { true },
    private val onClick: (AppInfo, IconView) -> Unit,
    private val onLongClick: (AppInfo, IconView) -> Boolean,
) : RecyclerView.Adapter<AppGridAdapter.VH>() {

    var items: List<AppInfo> = emptyList()
        private set

    class VH(val view: IconView) : RecyclerView.ViewHolder(view)

    fun submit(list: List<AppInfo>) {
        items = list
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = IconView(parent.context)
        v.layoutParams = RecyclerView.LayoutParams(itemWidth(), itemHeight())
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val app = items[position]
        val v = holder.view
        val lp = v.layoutParams
        lp.height = itemHeight()
        lp.width = itemWidth()
        v.layoutParams = lp
        v.icon = app.icon
        v.label = app.label
        v.showLabel = showLabels()
        v.setOnClickListener { onClick(app, v) }
        v.setOnLongClickListener { onLongClick(app, v) }
    }
}
