package com.nlauncher.ui

import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View

val Int.dp: Int get() = (this * Resources.getSystem().displayMetrics.density + 0.5f).toInt()
val Float.dp: Float get() = this * Resources.getSystem().displayMetrics.density
val Int.sp: Float get() = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, this.toFloat(), Resources.getSystem().displayMetrics)

fun roundedBg(color: Int, radiusDp: Int, strokeColor: Int = Color.TRANSPARENT): GradientDrawable =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusDp.dp.toFloat()
        if (strokeColor != Color.TRANSPARENT) setStroke(1.dp, strokeColor)
    }

fun View.selectableBorderless(context: Context) {
    val ta = context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackgroundBorderless))
    background = ta.getDrawable(0)
    ta.recycle()
}

fun View.selectable(context: Context) {
    val ta = context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
    background = ta.getDrawable(0)
    ta.recycle()
}

object Palette {
    val colors = intArrayOf(
        0x66FFFFFF, 0x00000000, 0xCC000000.toInt(), 0xFFFFFFFF.toInt(),
        0xFFE53935.toInt(), 0xFFFB8C00.toInt(), 0xFFFDD835.toInt(), 0xFF43A047.toInt(),
        0xFF00ACC1.toInt(), 0xFF1E88E5.toInt(), 0xFF3949AB.toInt(), 0xFF8E24AA.toInt(),
        0xFFD81B60.toInt(), 0xFF6D4C41.toInt(), 0xFF546E7A.toInt(), 0xFF26A69A.toInt(),
        0xFFFF8A80.toInt(), 0xFFFFD180.toInt(), 0xFFB9F6CA.toInt(), 0xFF80D8FF.toInt(),
        0xFFB388FF.toInt(), 0xFFF8BBD0.toInt(),
    )
}
