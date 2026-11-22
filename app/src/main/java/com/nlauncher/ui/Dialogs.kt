package com.nlauncher.ui

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nlauncher.data.AppInfo
import com.nlauncher.model.GroupIcon
import com.nlauncher.model.IconShape
import com.nlauncher.model.IconStyle
import com.nlauncher.model.Settings

private fun label(activity: Activity, text: String): TextView = TextView(activity).apply {
    this.text = text
    setTextColor(0xCCFFFFFF.toInt())
    textSize = 13f
    setPadding(0, 14.dp, 0, 6.dp)
}

/** アプリを複数選択するダイアログ */
fun showAppPicker(
    activity: Activity,
    title: String,
    apps: List<AppInfo>,
    preselected: Collection<String>,
    onDone: (List<String>) -> Unit,
) {
    val selected = LinkedHashSet(preselected)
    val root = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(16.dp, 8.dp, 16.dp, 0)
    }
    val search = EditText(activity).apply {
        hint = "検索"
        isSingleLine = true
    }
    root.addView(search)
    var filtered = apps

    val adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = filtered.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val row = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(4.dp, 6.dp, 4.dp, 6.dp)
                selectable(parent.context)
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            row.addView(ImageView(parent.context), LinearLayout.LayoutParams(36.dp, 36.dp))
            row.addView(TextView(parent.context).apply {
                textSize = 15f
                setPadding(14.dp, 0, 8.dp, 0)
                maxLines = 1
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(CheckBox(parent.context).apply { isClickable = false; isFocusable = false })
            return object : RecyclerView.ViewHolder(row) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val app = filtered[position]
            val row = holder.itemView as LinearLayout
            (row.getChildAt(0) as ImageView).setImageDrawable(app.icon)
            (row.getChildAt(1) as TextView).text = app.label
            val cb = row.getChildAt(2) as CheckBox
            cb.isChecked = app.key in selected
            row.setOnClickListener {
                if (!selected.remove(app.key)) selected.add(app.key)
                cb.isChecked = app.key in selected
            }
        }
    }
    val rv = RecyclerView(activity).apply {
        layoutManager = LinearLayoutManager(activity)
        this.adapter = adapter
    }
    root.addView(rv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (activity.resources.displayMetrics.heightPixels * 0.55f).toInt()))
    search.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            val q = s?.toString()?.trim()?.lowercase().orEmpty()
            filtered = if (q.isEmpty()) apps else apps.filter { it.label.lowercase().contains(q) }
            adapter.notifyDataSetChanged()
        }
    })
    AlertDialog.Builder(activity)
        .setTitle(title)
        .setView(root)
        .setPositiveButton("OK") { _, _ -> onDone(selected.toList()) }
        .setNegativeButton("キャンセル", null)
        .showStyled()
}

fun showTextInput(activity: Activity, title: String, initial: String, onDone: (String) -> Unit) {
    val et = EditText(activity).apply {
        setText(initial)
        setSelection(initial.length)
        isSingleLine = true
    }
    val wrap = FrameLayout(activity).apply {
        setPadding(20.dp, 8.dp, 20.dp, 0)
        addView(et)
    }
    AlertDialog.Builder(activity)
        .setTitle(title)
        .setView(wrap)
        .setPositiveButton("OK") { _, _ -> onDone(et.text.toString().trim()) }
        .setNegativeButton("キャンセル", null)
        .showStyled()
}

/**
 * グループアイコンの作成ダイアログ。
 * スタイル（アプリ並べ / 文字・絵文字 / 画像）、形、背景色、文字色を選べる。
 */
class GroupIconEditor(
    private val activity: Activity,
    initial: GroupIcon,
    private val render: (GroupIcon) -> Bitmap,
    private val onPickImage: () -> Unit,
    private val onSave: (GroupIcon) -> Unit,
) {
    val icon = initial.copy()
    private val preview = ImageView(activity)
    private lateinit var textInput: EditText
    private lateinit var textRow: View
    private lateinit var imageRow: View
    private val styleButtons = mutableMapOf<IconStyle, TextView>()
    private val shapeButtons = mutableMapOf<IconShape, TextView>()
    private var dialog: AlertDialog? = null

    fun show() {
        val scroll = ScrollView(activity)
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22.dp, 12.dp, 22.dp, 8.dp)
        }
        scroll.addView(root)

        val previewWrap = FrameLayout(activity).apply {
            background = roundedBg(0xFF3A3F55.toInt(), 16)
            setPadding(0, 16.dp, 0, 16.dp)
        }
        previewWrap.addView(preview, FrameLayout.LayoutParams(96.dp, 96.dp, Gravity.CENTER))
        root.addView(previewWrap, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        root.addView(label(activity, "スタイル"))
        root.addView(chipRow(listOf(
            IconStyle.PREVIEW to "アプリを並べる",
            IconStyle.TEXT to "文字・絵文字",
            IconStyle.IMAGE to "画像",
        ), styleButtons) { s ->
            icon.style = s
            if (s == IconStyle.IMAGE && icon.imagePath == null) onPickImage()
            refresh()
        })

        textInput = EditText(activity).apply {
            hint = "例: 📷 / 仕事 / G"
            setText(icon.text)
            isSingleLine = true
            filters = arrayOf(InputFilter.LengthFilter(8))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    icon.text = s?.toString().orEmpty()
                    refresh()
                }
            })
        }
        textRow = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(label(activity, "表示する文字（絵文字OK）"))
            addView(textInput)
            addView(label(activity, "文字色"))
            addView(colorRow(intArrayOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFF212121.toInt()) + Palette.colors.drop(4).toIntArray()) {
                icon.fgColor = it; refresh()
            })
        }
        root.addView(textRow)

        imageRow = TextView(activity).apply {
            text = "画像を選ぶ…"
            setTextColor(0xFF8AB4F8.toInt())
            textSize = 15f
            setPadding(0, 14.dp, 0, 6.dp)
            setOnClickListener { onPickImage() }
        }
        root.addView(imageRow)

        root.addView(label(activity, "形"))
        root.addView(chipRow(listOf(
            IconShape.CIRCLE to "丸",
            IconShape.ROUNDED to "角丸",
            IconShape.SQUIRCLE to "スクワークル",
            IconShape.SQUARE to "四角",
        ), shapeButtons) { s -> icon.shape = s; refresh() })

        root.addView(label(activity, "背景色"))
        root.addView(colorRow(Palette.colors) { icon.bgColor = it; refresh() })

        root.addView(label(activity, "背景の透明度"))
        root.addView(SeekBar(activity).apply {
            max = 255
            progress = Color.alpha(icon.bgColor)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) {
                        icon.bgColor = (icon.bgColor and 0x00FFFFFF) or (p shl 24)
                        refresh()
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        })

        refresh()
        dialog = AlertDialog.Builder(activity)
            .setTitle("グループアイコンを作成")
            .setView(scroll)
            .setPositiveButton("保存") { _, _ -> onSave(icon) }
            .setNegativeButton("キャンセル", null)
            .showStyled()
    }

    fun setImage(path: String) {
        icon.imagePath = path
        icon.style = IconStyle.IMAGE
        refresh()
    }

    private fun refresh() {
        preview.setImageBitmap(render(icon))
        textRow.visibility = if (icon.style == IconStyle.TEXT) View.VISIBLE else View.GONE
        imageRow.visibility = if (icon.style == IconStyle.IMAGE) View.VISIBLE else View.GONE
        styleButtons.forEach { (k, v) -> styleChip(v, k == icon.style) }
        shapeButtons.forEach { (k, v) -> styleChip(v, k == icon.shape) }
    }

    private fun styleChip(v: TextView, selected: Boolean) {
        v.background = roundedBg(if (selected) 0xFF8AB4F8.toInt() else 0x22FFFFFF, 18)
        v.setTextColor(if (selected) Color.BLACK else Color.WHITE)
    }

    private fun <T> chipRow(options: List<Pair<T, String>>, store: MutableMap<T, TextView>, onSelect: (T) -> Unit): View {
        val hs = HorizontalScrollView(activity).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        for ((value, text) in options) {
            val chip = TextView(activity).apply {
                this.text = text
                textSize = 14f
                setPadding(14.dp, 8.dp, 14.dp, 8.dp)
                setOnClickListener { onSelect(value) }
            }
            store[value] = chip
            row.addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = 8.dp })
        }
        hs.addView(row)
        return hs
    }

    private fun colorRow(colors: IntArray, onSelect: (Int) -> Unit): View {
        val hs = HorizontalScrollView(activity).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 4.dp, 0, 4.dp) }
        for (c in colors) {
            val sw = View(activity).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(c)
                    setStroke(1.dp, 0x88FFFFFF.toInt())
                }
                setOnClickListener { onSelect(c) }
            }
            row.addView(sw, LinearLayout.LayoutParams(34.dp, 34.dp).apply { marginEnd = 8.dp })
        }
        hs.addView(row)
        return hs
    }
}

/** グリッド設定 */
fun showSettingsDialog(activity: Activity, settings: Settings, onDone: (Settings) -> Unit) {
    val s = Settings(settings.cols, settings.rows, settings.showLabels, settings.drawerCols)
    val root = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(22.dp, 8.dp, 22.dp, 8.dp)
    }
    fun seek(title: String, min: Int, max: Int, value: Int, set: (Int) -> Unit) {
        val l = label(activity, "$title: $value")
        root.addView(l)
        root.addView(SeekBar(activity).apply {
            this.max = max - min
            progress = value - min
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    set(p + min)
                    l.text = "$title: ${p + min}"
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        })
    }
    seek("ホームの列数", 2, 12, s.cols) { s.cols = it }
    seek("ホームの行数", 2, 16, s.rows) { s.rows = it }
    seek("ドロワーの列数", 3, 8, s.drawerCols) { s.drawerCols = it }
    root.addView(Switch(activity).apply {
        text = "ホームにアプリ名を表示"
        isChecked = s.showLabels
        setPadding(0, 16.dp, 0, 8.dp)
        setOnCheckedChangeListener { _, c -> s.showLabels = c }
    })
    AlertDialog.Builder(activity)
        .setTitle("設定")
        .setView(ScrollView(activity).apply { addView(root) })
        .setPositiveButton("OK") { _, _ -> onDone(s) }
        .setNegativeButton("キャンセル", null)
        .showStyled()
}
