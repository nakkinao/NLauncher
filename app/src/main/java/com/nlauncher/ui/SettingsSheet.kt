package com.nlauncher.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import com.nlauncher.model.AppIconShape
import com.nlauncher.model.IconShape
import com.nlauncher.model.IconStyle
import com.nlauncher.model.LabelFont
import com.nlauncher.model.Settings
import com.nlauncher.model.Theme

/**
 * すべての見た目の設定を1画面でまとめて変更できるシート。
 * 変更はすぐホーム画面に反映される（上半分がライブプレビューになる）。
 */
class SettingsSheet(
    private val activity: Activity,
    startTheme: Theme,
    startSettings: Settings,
    private val host: Host,
) : FrameLayout(activity) {

    interface Host {
        fun onThemePreview(theme: Theme)
        fun onSettingsDone(theme: Theme, settings: Settings)
        fun onSettingsCancel(original: Theme)
        fun onBulkGroupIcons(shape: IconShape?, style: IconStyle?, bgColor: Int?, bgAlpha: Int?)
        fun onBulkGroupMode(panel: Boolean)
        fun onCopyTheme(theme: Theme)
        fun onPasteTheme(): Theme?
    }

    private val original = startTheme.copy()
    private var theme = startTheme.copy()
    private val settings = Settings(startSettings.cols, startSettings.rows, startSettings.showLabels, startSettings.drawerCols)

    private val sheet: LinearLayout
    private val scroll: ScrollView
    private val content: LinearLayout
    private val sectionViews = LinkedHashMap<String, View>()
    private var expanded = false

    // 一括グループアイコン用
    private var bulkShape: IconShape = IconShape.SQUIRCLE
    private var bulkStyle: IconStyle? = null
    private var bulkBg: Int = 0xFFFFFFFF.toInt()
    private var bulkAlpha: Int = 30

    private val fg: Int get() = Look.textOn(theme.folderColor, (theme.folderAlpha + 20).coerceAtMost(100))
    private val sub: Int get() = Look.subTextOn(theme.folderColor, (theme.folderAlpha + 20).coerceAtMost(100))

    init {
        // 上部（プレビュー部分）のタッチはホームに渡さず無視する
        isClickable = true
        sheet = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        scroll = ScrollView(activity).apply {
            isVerticalScrollBarEnabled = false
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(18.dp)
        }
        content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp, 0, 20.dp, 32.dp)
        }
        scroll.addView(content)
        addView(sheet, LayoutParams(LayoutParams.MATCH_PARENT, sheetHeight(), Gravity.BOTTOM))
        rebuild()
    }

    private fun sheetHeight(): Int {
        val h = activity.resources.displayMetrics.heightPixels
        return (h * if (expanded) 0.9f else 0.6f).toInt()
    }

    private fun toggleExpand() {
        expanded = !expanded
        val lp = sheet.layoutParams as LayoutParams
        lp.height = sheetHeight()
        sheet.layoutParams = lp
    }

    fun open(parent: ViewGroup) {
        parent.addView(this, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        sheet.translationY = sheetHeight().toFloat()
        sheet.animate().translationY(0f).setInterpolator(DecelerateInterpolator(2.2f)).setDuration(320).start()
    }

    private fun close() {
        sheet.animate().translationY(sheet.height.toFloat()).setDuration(220).withEndAction {
            (parent as? ViewGroup)?.removeView(this)
        }.start()
    }

    fun cancel() {
        host.onSettingsCancel(original)
        close()
    }

    fun finish() {
        host.onSettingsDone(theme.copy(), settings)
        close()
    }

    private fun changed() {
        host.onThemePreview(theme.copy())
        styleSheet()
    }

    private fun styleSheet() {
        sheet.background = Look.glass(theme.folderColor, (theme.folderAlpha + 20).coerceAtMost(100), 30, 14, topRadiusOnly = true)
    }

    // ------------------------------------------------------------ build

    private fun rebuild() {
        val y = scroll.scrollY
        sheet.removeAllViews()
        content.removeAllViews()
        sectionViews.clear()
        styleSheet()

        // ヘッダー
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(20.dp, 6.dp, 12.dp, 4.dp)
        }
        val grab = View(activity).apply { background = roundedBg(Look.withAlpha(fg, 35), 2) }
        sheet.addView(FrameLayout(activity).apply {
            setPadding(0, 10.dp, 0, 4.dp)
            addView(grab, LayoutParams(40.dp, 4.dp, Gravity.CENTER))
            setOnClickListener { toggleExpand() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 20.dp))
        header.addView(TextView(activity).apply {
            text = "デザイン設定"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(fg)
            setOnClickListener { toggleExpand() }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(pill("元に戻す", filled = false) { cancel() })
        header.addView(pill("完了", filled = true) { finish() })
        sheet.addView(header)

        // セクションへのジャンプ
        val jumpRow = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; setPadding(16.dp, 6.dp, 16.dp, 8.dp) }
        sheet.addView(HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            addView(jumpRow)
        })
        sheet.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        buildPresets()
        buildGeneral()
        buildWallpaper()
        buildIcons()
        buildDrawer()
        buildFolder()
        buildPanel()
        buildGroupBulk()
        buildGrid()
        buildShare()

        for ((name, v) in sectionViews) {
            jumpRow.addView(chipView(name, false) { scroll.smoothScrollTo(0, v.top - 4.dp) })
        }
        scroll.post { scroll.scrollTo(0, y) }
    }

    private fun section(name: String, note: String? = null) {
        val t = TextView(activity).apply {
            text = name
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.06f
            setTextColor(theme.accent)
            setPadding(0, 22.dp, 0, if (note == null) 4.dp else 0)
        }
        content.addView(t)
        sectionViews[name] = t
        if (note != null) content.addView(TextView(activity).apply {
            text = note
            textSize = 12f
            setTextColor(sub)
            setPadding(0, 2.dp, 0, 4.dp)
        })
    }

    private fun rowLabel(text: String, value: String? = null): Pair<View, TextView?> {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 12.dp, 0, 2.dp)
        }
        row.addView(TextView(activity).apply {
            this.text = text
            textSize = 14f
            setTextColor(fg)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        var v: TextView? = null
        if (value != null) {
            v = TextView(activity).apply {
                this.text = value
                textSize = 13f
                setTextColor(sub)
                typeface = Typeface.MONOSPACE
            }
            row.addView(v)
        }
        content.addView(row)
        return row to v
    }

    private fun slider(label: String, value: Int, min: Int, max: Int, suffix: String = "", onChange: (Int) -> Unit) {
        val (_, valueView) = rowLabel(label, "$value$suffix")
        content.addView(SeekBar(activity).apply {
            this.max = max - min
            progress = value - min
            progressTintList = android.content.res.ColorStateList.valueOf(theme.accent)
            thumbTintList = android.content.res.ColorStateList.valueOf(theme.accent)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Look.withAlpha(fg, 30))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    valueView?.text = "${p + min}$suffix"
                    onChange(p + min)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 32.dp))
    }

    private fun colorRow(label: String, value: Int, onChange: (Int) -> Unit) {
        rowLabel(label, String.format("#%06X", value and 0xFFFFFF))
        val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 6.dp, 0, 4.dp) }
        // カスタム（虹色）
        row.addView(View(activity).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, IntArray(6) { Color.HSVToColor(floatArrayOf(it * 60f, 0.8f, 1f)) }).apply {
                shape = GradientDrawable.OVAL
                setStroke(2.dp, Color.WHITE)
            }
            contentDescription = "カスタム"
            setOnClickListener {
                showColorPicker(activity, label, value) { c -> onChange(c); rebuild() }
            }
        }, LinearLayout.LayoutParams(34.dp, 34.dp).apply { marginEnd = 10.dp })
        val colors = (listOf(value) + Palette.colors.filter { Color.alpha(it) == 0xFF }).distinct()
        for (c in colors) {
            row.addView(View(activity).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(c)
                    if (c == value) setStroke(3.dp, theme.accent) else setStroke(1.dp, 0x55FFFFFF)
                }
                setOnClickListener { onChange(c); rebuild() }
            }, LinearLayout.LayoutParams(34.dp, 34.dp).apply { marginEnd = 8.dp })
        }
        content.addView(HorizontalScrollView(activity).apply { isHorizontalScrollBarEnabled = false; addView(row) })
    }

    private fun <T> chips(label: String?, options: List<Pair<T, String>>, selected: T?, onSelect: (T) -> Unit) {
        if (label != null) rowLabel(label)
        val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 6.dp, 0, 2.dp) }
        for ((v, text) in options) row.addView(chipView(text, v == selected) { onSelect(v); rebuild() })
        content.addView(HorizontalScrollView(activity).apply { isHorizontalScrollBarEnabled = false; addView(row) })
    }

    private fun toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
        chips(label, listOf(true to "オン", false to "オフ"), value, onChange)
    }

    private fun chipView(text: String, selected: Boolean, onClick: () -> Unit): TextView = TextView(activity).apply {
        this.text = text
        textSize = 13f
        setPadding(14.dp, 8.dp, 14.dp, 8.dp)
        background = roundedBg(if (selected) theme.accent else Look.withAlpha(fg, 10), 18, if (selected) 0 else Look.withAlpha(fg, 18))
        setTextColor(if (selected) Look.textOn(theme.accent, 100) else fg)
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = 8.dp }
    }

    private fun pill(text: String, filled: Boolean, onClick: () -> Unit): TextView = TextView(activity).apply {
        this.text = text
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        setPadding(16.dp, 8.dp, 16.dp, 8.dp)
        background = roundedBg(if (filled) theme.accent else Look.withAlpha(fg, 10), 20)
        setTextColor(if (filled) Look.textOn(theme.accent, 100) else fg)
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = 8.dp }
    }

    private fun actionButton(text: String, onClick: () -> Unit) {
        content.addView(TextView(activity).apply {
            this.text = text
            textSize = 14f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setPadding(16.dp, 12.dp, 16.dp, 12.dp)
            background = roundedBg(Look.withAlpha(theme.accent, 20), 16, Look.withAlpha(theme.accent, 50))
            setTextColor(theme.accent)
            setOnClickListener { onClick() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 10.dp })
    }

    // ------------------------------------------------------------ sections

    private fun buildPresets() {
        section("プリセット", "タップすると全項目をまとめて変更します")
        val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8.dp, 0, 4.dp) }
        for (p in Theme.presets) {
            val card = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.BOTTOM
                setPadding(10.dp, 8.dp, 10.dp, 8.dp)
                background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(
                    Look.blend(p.drawerColor, Color.BLACK, 0.2f), p.accent,
                )).apply {
                    cornerRadius = 18.dp.toFloat()
                    if (p.name == theme.name) setStroke(3.dp, Color.WHITE) else setStroke(1.dp, 0x33FFFFFF)
                }
                setOnClickListener {
                    theme = p.copy()
                    changed()
                    rebuild()
                }
            }
            // ミニプレビュー（アイコン3つ）
            val icons = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
            repeat(3) {
                icons.addView(View(activity).apply {
                    background = object : android.graphics.drawable.Drawable() {
                        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = Look.withAlpha(Color.WHITE, 85) }
                        override fun draw(canvas: android.graphics.Canvas) {
                            canvas.drawPath(Look.shapePath(if (p.iconShape == AppIconShape.SYSTEM) AppIconShape.CIRCLE else p.iconShape, android.graphics.RectF(bounds)), paint)
                        }
                        override fun setAlpha(alpha: Int) {}
                        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
                        @Deprecated("Deprecated in Java")
                        override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
                    }
                }, LinearLayout.LayoutParams(16.dp, 16.dp).apply { marginEnd = 4.dp })
            }
            card.addView(icons)
            card.addView(TextView(activity).apply {
                text = p.name
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                setShadowLayer(3f, 0f, 1f, 0x99000000.toInt())
                setPadding(0, 6.dp, 0, 0)
            })
            row.addView(card, LinearLayout.LayoutParams(92.dp, 76.dp).apply { marginEnd = 10.dp })
        }
        content.addView(HorizontalScrollView(activity).apply { isHorizontalScrollBarEnabled = false; addView(row) })
    }

    private fun buildGeneral() {
        section("全体")
        colorRow("アクセントカラー", theme.accent) { theme.accent = it; changed() }
        chips("フォント", LabelFont.values().map { it to Look.fontLabel(it) }, theme.labelFont) { theme.labelFont = it; changed() }
    }

    private fun buildWallpaper() {
        section("壁紙", "ぼかしは Android 12 以上で対応端末のみ")
        slider("ぼかし", theme.wallBlur, 0, 100) { theme.wallBlur = it; changed() }
        slider("暗さ", theme.wallDim, 0, 90, "%") { theme.wallDim = it; changed() }
    }

    private fun buildIcons() {
        section("アイコン")
        chips("アイコンの形（全アプリ）", AppIconShape.values().map { it to Look.shapeLabel(it) }, theme.iconShape) { theme.iconShape = it; changed() }
        slider("アイコンの大きさ", theme.iconScale, 50, 150, "%") { theme.iconScale = it; changed() }
        chips("ホームにアプリ名を表示", listOf(true to "オン", false to "オフ"), settings.showLabels) { settings.showLabels = it }
        colorRow("アプリ名の色", theme.labelColor) { theme.labelColor = it; changed() }
        slider("アプリ名の大きさ", theme.labelSize, 8, 18, "sp") { theme.labelSize = it; changed() }
        toggle("アプリ名の影", theme.labelShadow) { theme.labelShadow = it; changed() }
    }

    private fun buildDrawer() {
        section("アプリ一覧")
        colorRow("背景色", theme.drawerColor) { theme.drawerColor = it; changed() }
        slider("不透明度", theme.drawerAlpha, 0, 100, "%") { theme.drawerAlpha = it; changed() }
        slider("ぼかし", theme.drawerBlur, 0, 100) { theme.drawerBlur = it; changed() }
    }

    private fun buildFolder() {
        section("開いたグループ・メニュー")
        colorRow("背景色", theme.folderColor) { theme.folderColor = it; changed() }
        slider("不透明度", theme.folderAlpha, 0, 100, "%") { theme.folderAlpha = it; changed() }
        slider("ぼかし", theme.folderBlur, 0, 100) { theme.folderBlur = it; changed() }
        slider("角の丸み", theme.folderRadius, 0, 48, "dp") { theme.folderRadius = it; changed() }
    }

    private fun buildPanel() {
        section("ホームのパネル（スクロールするグループ）")
        colorRow("背景色", theme.panelColor) { theme.panelColor = it; changed() }
        slider("不透明度", theme.panelAlpha, 0, 100, "%") { theme.panelAlpha = it; changed() }
        slider("角の丸み", theme.panelRadius, 0, 48, "dp") { theme.panelRadius = it; changed() }
        slider("枠線の濃さ", theme.panelBorder, 0, 100, "%") { theme.panelBorder = it; changed() }
    }

    private fun buildGroupBulk() {
        section("グループを一括変更", "ここで選んだ内容を、すべてのグループにまとめて適用します")
        chips("アイコンの形", listOf(
            IconShape.CIRCLE to "丸", IconShape.ROUNDED to "角丸", IconShape.SQUIRCLE to "スクワークル", IconShape.SQUARE to "四角",
        ), bulkShape) { bulkShape = it }
        chips("アイコンのスタイル", listOf<Pair<IconStyle?, String>>(
            null to "変更しない", IconStyle.PREVIEW to "アプリを並べる", IconStyle.TEXT to "文字・絵文字",
        ), bulkStyle) { bulkStyle = it }
        colorRow("アイコンの背景色", bulkBg) { bulkBg = it }
        slider("アイコン背景の不透明度", bulkAlpha, 0, 100, "%") { bulkAlpha = it }
        actionButton("すべてのグループアイコンに適用") {
            host.onBulkGroupIcons(bulkShape, bulkStyle, bulkBg, bulkAlpha)
        }
        actionButton("すべてのグループをパネル表示（ホームでスクロール）") { host.onBulkGroupMode(true) }
        actionButton("すべてのグループをフォルダ表示") { host.onBulkGroupMode(false) }
    }

    private fun buildGrid() {
        section("グリッド", "「完了」を押すと反映されます")
        slider("ホームの列数", settings.cols, 2, 12) { settings.cols = it }
        slider("ホームの行数", settings.rows, 2, 16) { settings.rows = it }
        slider("アプリ一覧の列数", settings.drawerCols, 3, 8) { settings.drawerCols = it }
    }

    private fun buildShare() {
        section("テーマの共有")
        actionButton("テーマをクリップボードにコピー") { host.onCopyTheme(theme) }
        actionButton("クリップボードから貼り付け") {
            host.onPasteTheme()?.let { theme = it; changed(); rebuild() }
        }
        actionButton("初期設定に戻す") { theme = Theme(); changed(); rebuild() }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = false
}
