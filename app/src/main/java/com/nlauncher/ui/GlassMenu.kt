package com.nlauncher.ui

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.os.Build
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** タップ位置の近くに出る、すりガラスのポップアップメニュー */
class GlassMenu(
    context: Context,
    title: String?,
    entries: List<Pair<String, () -> Unit>>,
    private val anchorX: Float,
    private val anchorY: Float,
    private val onDismiss: () -> Unit,
) : FrameLayout(context) {

    private val card: LinearLayout
    private var dismissed = false

    init {
        isClickable = true
        setOnClickListener { dismiss() }
        val t = Look.theme
        val fg = Look.textOn(t.folderColor, t.folderAlpha)
        val sub = Look.subTextOn(t.folderColor, t.folderAlpha)
        card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = Look.glass(t.folderColor, (t.folderAlpha + 15).coerceAtMost(100), (t.folderRadius * 0.7f).toInt().coerceAtLeast(10), 18)
            setPadding(6.dp, 8.dp, 6.dp, 8.dp)
            isClickable = true
            elevation = 8f.dp
        }
        if (!title.isNullOrEmpty()) {
            card.addView(TextView(context).apply {
                text = title
                setTextColor(sub)
                textSize = 12f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                letterSpacing = 0.04f
                setPadding(14.dp, 4.dp, 14.dp, 6.dp)
            })
        }
        val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        for ((label, action) in entries) {
            list.addView(TextView(context).apply {
                text = label
                setTextColor(fg)
                textSize = 15f
                typeface = Look.typeface(t.labelFont)
                gravity = Gravity.CENTER_VERTICAL
                minHeight = 46.dp
                setPadding(14.dp, 0, 14.dp, 0)
                background = pressBg(t.accent)
                setOnClickListener {
                    dismiss()
                    post { action() }
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val scroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            addView(list)
        }
        card.addView(scroll)
        addView(card, LayoutParams(250.dp, LayoutParams.WRAP_CONTENT))
    }

    private fun pressBg(accent: Int): android.graphics.drawable.Drawable {
        val sl = android.graphics.drawable.StateListDrawable()
        sl.addState(intArrayOf(android.R.attr.state_pressed), roundedBg(Look.withAlpha(accent, 22), 14))
        sl.addState(intArrayOf(), android.graphics.drawable.ColorDrawable(0))
        return sl
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val w = width
        val h = height
        val cw = card.width
        val maxH = (h * 0.7f).toInt()
        if (card.height > maxH) {
            card.layoutParams = (card.layoutParams as LayoutParams).apply { height = maxH }
            return
        }
        val ch = card.height
        val m = 12.dp
        val x = (anchorX - cw / 2f).coerceIn(m.toFloat(), (w - cw - m).toFloat().coerceAtLeast(m.toFloat()))
        var y = if (anchorY + ch + m < h) anchorY + 8.dp else anchorY - ch - 8.dp
        y = y.coerceIn(m.toFloat(), (h - ch - m).toFloat().coerceAtLeast(m.toFloat()))
        card.x = x
        card.y = y
        card.pivotX = (anchorX - x).coerceIn(0f, cw.toFloat())
        card.pivotY = if (y > anchorY) 0f else ch.toFloat()
    }

    fun show(parent: ViewGroup) {
        parent.addView(this, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        alpha = 0f
        card.scaleX = 0.85f; card.scaleY = 0.85f
        animate().alpha(1f).setDuration(140).start()
        card.animate().scaleX(1f).scaleY(1f).setInterpolator(DecelerateInterpolator(2f)).setDuration(200).start()
    }

    fun dismiss() {
        if (dismissed) return
        dismissed = true
        animate().alpha(0f).setDuration(120).withEndAction {
            (parent as? ViewGroup)?.removeView(this)
        }.start()
        onDismiss()
    }
}

/** ダイアログにテーマの背景とぼかしを適用して表示する */
fun AlertDialog.Builder.showStyled(): AlertDialog {
    val d = create()
    d.styleGlass()
    d.show()
    d.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Look.theme.accent)
    d.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Look.withAlpha(Look.theme.accent, 75))
    d.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(Look.withAlpha(Look.theme.accent, 75))
    return d
}

fun Dialog.styleGlass() {
    val t = Look.theme
    val w = window ?: return
    // ダイアログは読みやすさのため少し濃いめ
    val color = if (Look.isLight(t.folderColor) && t.folderAlpha >= 35) 0xFF2A2D3A.toInt() else t.folderColor
    w.setBackgroundDrawable(Look.glass(color, (t.folderAlpha + 20).coerceIn(80, 100), t.folderRadius.coerceAtLeast(12), 14))
    if (Build.VERSION.SDK_INT >= 31 && t.folderBlur > 0) {
        w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        w.attributes = w.attributes.apply { blurBehindRadius = Look.blurPx(t.folderBlur) }
    }
    w.setDimAmount(0.35f)
}
