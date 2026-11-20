package com.nlauncher

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nlauncher.ui.dp
import com.nlauncher.ui.roundedBg
import com.nlauncher.ui.selectable
import java.text.Collator
import java.util.concurrent.Executors

/** インストール済みのウィジェットをすべて一覧表示して選ばせる */
class WidgetPickerActivity : Activity() {

    private sealed class Row {
        class Header(val title: String, val icon: Drawable?) : Row()
        class Widget(val info: AppWidgetProviderInfo, val label: String) : Row()
    }

    private var all: List<Row> = emptyList()
    private var shown: List<Row> = emptyList()
    private lateinit var adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>
    private val previews = HashMap<AppWidgetProviderInfo, Drawable?>()
    private val executor = Executors.newFixedThreadPool(2)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val title = TextView(this).apply {
            text = "ウィジェットを追加"
            setTextColor(Color.WHITE)
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(20.dp, 16.dp, 20.dp, 4.dp)
        }
        root.addView(title)
        val search = EditText(this).apply {
            hint = "検索"
            setHintTextColor(0x88FFFFFF.toInt())
            setTextColor(Color.WHITE)
            isSingleLine = true
            background = roundedBg(0x22FFFFFF, 22)
            setPadding(18.dp, 10.dp, 18.dp, 10.dp)
        }
        root.addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(16.dp, 8.dp, 16.dp, 8.dp)
        })

        adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount() = shown.size
            override fun getItemViewType(position: Int) = if (shown[position] is Row.Header) 0 else 1

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val ctx = parent.context
                val v = if (viewType == 0) {
                    LinearLayout(ctx).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(20.dp, 18.dp, 20.dp, 6.dp)
                        addView(ImageView(ctx), LinearLayout.LayoutParams(28.dp, 28.dp))
                        addView(TextView(ctx).apply {
                            setTextColor(Color.WHITE)
                            textSize = 16f
                            typeface = Typeface.DEFAULT_BOLD
                            setPadding(12.dp, 0, 0, 0)
                        })
                    }
                } else {
                    LinearLayout(ctx).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(20.dp, 8.dp, 20.dp, 8.dp)
                        selectable(ctx)
                        addView(ImageView(ctx).apply {
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            background = roundedBg(0x18FFFFFF, 12)
                            setPadding(6.dp, 6.dp, 6.dp, 6.dp)
                        }, LinearLayout.LayoutParams(120.dp, 90.dp))
                        addView(LinearLayout(ctx).apply {
                            orientation = LinearLayout.VERTICAL
                            setPadding(16.dp, 0, 0, 0)
                            addView(TextView(ctx).apply { setTextColor(Color.WHITE); textSize = 15f })
                            addView(TextView(ctx).apply { setTextColor(0x99FFFFFF.toInt()); textSize = 13f })
                        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    }
                }
                v.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                return object : RecyclerView.ViewHolder(v) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val row = shown[position]
                val v = holder.itemView as LinearLayout
                when (row) {
                    is Row.Header -> {
                        (v.getChildAt(0) as ImageView).setImageDrawable(row.icon)
                        (v.getChildAt(1) as TextView).text = row.title
                    }
                    is Row.Widget -> {
                        val img = v.getChildAt(0) as ImageView
                        val texts = v.getChildAt(1) as LinearLayout
                        (texts.getChildAt(0) as TextView).text = row.label
                        val dm = resources.displayMetrics
                        val w = row.info.minWidth / dm.density
                        val h = row.info.minHeight / dm.density
                        (texts.getChildAt(1) as TextView).text = "最小 ${w.toInt()}×${h.toInt()}dp"
                        img.tag = row.info
                        if (previews.containsKey(row.info)) {
                            img.setImageDrawable(previews[row.info])
                        } else {
                            img.setImageDrawable(null)
                            val info = row.info
                            executor.execute {
                                val d = try {
                                    info.loadPreviewImage(this@WidgetPickerActivity, 0)
                                        ?: info.loadIcon(this@WidgetPickerActivity, 0)
                                } catch (e: Exception) { null }
                                runOnUiThread {
                                    previews[info] = d
                                    if (img.tag === info) img.setImageDrawable(d)
                                }
                            }
                        }
                        v.setOnClickListener { pick(row.info) }
                    }
                }
            }
        }

        val rv = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@WidgetPickerActivity)
            adapter = this@WidgetPickerActivity.adapter
            clipToPadding = false
        }
        root.addView(rv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, bars.top, 0, 0)
            rv.setPadding(0, 0, 0, bars.bottom)
            insets
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = filter(s?.toString().orEmpty())
        })

        load()
    }

    private fun load() {
        val awm = AppWidgetManager.getInstance(this)
        val pm = packageManager
        val providers = try { awm.installedProviders } catch (e: Exception) { emptyList() }
        // 他プロファイル（仕事用など）も含める
        val um = getSystemService(android.os.UserManager::class.java)
        val extra = try {
            um.userProfiles.filter { it != android.os.Process.myUserHandle() }
                .flatMap { awm.getInstalledProvidersForProfile(it) }
        } catch (e: Exception) { emptyList() }
        val collator = Collator.getInstance()
        val byPkg = (providers + extra).groupBy { it.provider.packageName + "#" + it.profile.hashCode() }
        val rows = mutableListOf<Row>()
        val groups = byPkg.values.map { list ->
            val pkg = list.first().provider.packageName
            val appLabel = try {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (e: Exception) { pkg }
            val icon = try { pm.getApplicationIcon(pkg) } catch (e: Exception) { null }
            Triple(appLabel, icon, list)
        }.sortedWith { a, b -> collator.compare(a.first, b.first) }
        for ((label, icon, list) in groups) {
            rows += Row.Header(label, icon)
            list.map { it to (it.loadLabel(pm) ?: label) }
                .sortedWith { a, b -> collator.compare(a.second, b.second) }
                .forEach { (info, l) -> rows += Row.Widget(info, l) }
        }
        all = rows
        filter("")
    }

    private fun filter(q: String) {
        val query = q.trim().lowercase()
        if (query.isEmpty()) {
            shown = all
        } else {
            val out = mutableListOf<Row>()
            var header: Row.Header? = null
            var headerAdded = false
            for (r in all) {
                when (r) {
                    is Row.Header -> { header = r; headerAdded = false }
                    is Row.Widget -> {
                        if (r.label.lowercase().contains(query) || (header?.title?.lowercase()?.contains(query) == true)) {
                            if (!headerAdded && header != null) { out += header; headerAdded = true }
                            out += r
                        }
                    }
                }
            }
            shown = out
        }
        adapter.notifyDataSetChanged()
    }

    private fun pick(info: AppWidgetProviderInfo) {
        setResult(RESULT_OK, Intent()
            .putExtra(EXTRA_PROVIDER, info.provider)
            .putExtra(EXTRA_USER, info.profile))
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdownNow()
    }

    companion object {
        const val EXTRA_PROVIDER = "provider"
        const val EXTRA_USER = "user"
    }
}
