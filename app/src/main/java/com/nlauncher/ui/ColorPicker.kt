package com.nlauncher.ui

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

/** 色相・彩度・明るさ・16進コードで自由に色を作るピッカー */
fun showColorPicker(activity: Activity, title: String, initial: Int, onPick: (Int) -> Unit) {
    val hsv = FloatArray(3)
    Color.colorToHSV(initial or 0xFF000000.toInt(), hsv)
    val root = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(22.dp, 12.dp, 22.dp, 4.dp)
    }
    val preview = View(activity)
    root.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 64.dp))
    val hex = EditText(activity).apply {
        isSingleLine = true
        textSize = 16f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        typeface = android.graphics.Typeface.MONOSPACE
    }
    root.addView(hex, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        topMargin = 6.dp
    })

    var updatingHex = false
    val bars = mutableListOf<SeekBar>()
    lateinit var satTrack: GradientDrawable
    lateinit var valTrack: GradientDrawable

    fun current() = Color.HSVToColor(hsv)

    fun refresh(fromHex: Boolean = false) {
        val c = current()
        preview.background = roundedBg(c, 16, 0x33FFFFFF)
        satTrack.colors = intArrayOf(Color.HSVToColor(floatArrayOf(hsv[0], 0f, hsv[2])), Color.HSVToColor(floatArrayOf(hsv[0], 1f, hsv[2])))
        valTrack.colors = intArrayOf(Color.BLACK, Color.HSVToColor(floatArrayOf(hsv[0], hsv[1], 1f)))
        if (!fromHex) {
            updatingHex = true
            hex.setText(String.format("#%06X", c and 0xFFFFFF))
            updatingHex = false
        }
    }

    fun track(colors: IntArray): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors).apply { cornerRadius = 8.dp.toFloat() }

    fun bar(label: String, max: Int, value: Int, bg: GradientDrawable, onChange: (Int) -> Unit) {
        root.addView(TextView(activity).apply {
            text = label
            setTextColor(0xB3FFFFFF.toInt())
            textSize = 12f
            setPadding(0, 12.dp, 0, 4.dp)
        })
        val sb = SeekBar(activity).apply {
            this.max = max
            progress = value
            progressDrawable = LayerDrawable(arrayOf(bg, ClipDrawable(GradientDrawable().apply { setColor(0) }, Gravity.START, ClipDrawable.HORIZONTAL)))
            minimumHeight = 16.dp
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) { onChange(p); refresh() }
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        bars += sb
        root.addView(sb, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 28.dp))
    }

    val hueColors = IntArray(7) { Color.HSVToColor(floatArrayOf(it * 60f, 1f, 1f)) }
    satTrack = track(intArrayOf(Color.WHITE, Color.RED))
    valTrack = track(intArrayOf(Color.BLACK, Color.WHITE))
    bar("色相", 360, hsv[0].toInt(), track(hueColors)) { hsv[0] = it.toFloat() }
    bar("鮮やかさ", 100, (hsv[1] * 100).toInt(), satTrack) { hsv[1] = it / 100f }
    bar("明るさ", 100, (hsv[2] * 100).toInt(), valTrack) { hsv[2] = it / 100f }

    hex.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            if (updatingHex) return
            val t = s?.toString()?.trim()?.removePrefix("#") ?: return
            if (t.length != 6) return
            val c = t.toLongOrNull(16) ?: return
            Color.colorToHSV((c.toInt() or 0xFF000000.toInt()), hsv)
            bars[0].progress = hsv[0].toInt()
            bars[1].progress = (hsv[1] * 100).toInt()
            bars[2].progress = (hsv[2] * 100).toInt()
            refresh(fromHex = true)
        }
    })

    // よく使う色
    val quick = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 14.dp, 0, 4.dp) }
    val hs = android.widget.HorizontalScrollView(activity).apply { isHorizontalScrollBarEnabled = false; addView(quick) }
    for (c in Palette.colors) {
        if (Color.alpha(c) != 0xFF) continue
        quick.addView(View(activity).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c); setStroke(1.dp, 0x55FFFFFF) }
            setOnClickListener {
                Color.colorToHSV(c, hsv)
                bars[0].progress = hsv[0].toInt()
                bars[1].progress = (hsv[1] * 100).toInt()
                bars[2].progress = (hsv[2] * 100).toInt()
                refresh()
            }
        }, LinearLayout.LayoutParams(30.dp, 30.dp).apply { marginEnd = 8.dp })
    }
    root.addView(hs)

    refresh()
    AlertDialog.Builder(activity)
        .setTitle(title)
        .setView(FrameLayout(activity).apply { addView(android.widget.ScrollView(activity).apply { addView(root) }) })
        .setPositiveButton("決定") { _, _ -> onPick(current()) }
        .setNegativeButton("キャンセル", null)
        .showStyled()
}
