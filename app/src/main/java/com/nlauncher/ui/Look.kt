package com.nlauncher.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import com.nlauncher.model.AppIconShape
import com.nlauncher.model.LabelFont
import com.nlauncher.model.Theme

/** 現在のテーマと、テーマに応じた描画ヘルパー */
object Look {

    var theme: Theme = Theme()
        set(value) {
            field = value
            iconCache.clear()
            version++
        }

    /** テーマが変わるたびに増える（キャッシュ無効化用） */
    var version = 0
        private set

    private val iconCache = HashMap<String, Drawable>()

    fun invalidateIcons() {
        iconCache.clear()
        version++
    }

    fun withAlpha(color: Int, pct: Int): Int =
        (color and 0x00FFFFFF) or ((pct.coerceIn(0, 100) * 255 / 100) shl 24)

    fun luminance(color: Int): Double {
        fun ch(c: Int): Double {
            val v = c / 255.0
            return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * ch(Color.red(color)) + 0.7152 * ch(Color.green(color)) + 0.0722 * ch(Color.blue(color))
    }

    fun isLight(color: Int) = luminance(color) > 0.45

    /** 半透明の背景色の上に置く文字色。背景が明るくしっかり塗られていれば濃い色 */
    fun textOn(color: Int, alphaPct: Int): Int =
        if (isLight(color) && alphaPct >= 35) 0xFF15171F.toInt() else Color.WHITE

    fun subTextOn(color: Int, alphaPct: Int): Int =
        if (isLight(color) && alphaPct >= 35) 0x9915171F.toInt() else 0xB3FFFFFF.toInt()

    /** すりガラス風の背景（上が少し明るいグラデーション + 細い縁） */
    fun glass(color: Int, alphaPct: Int, radiusDp: Int, borderPct: Int = 16, topRadiusOnly: Boolean = false): GradientDrawable {
        val base = withAlpha(color, alphaPct)
        val lift = if (isLight(color)) 0 else 6
        val top = withAlpha(blend(color, Color.WHITE, 0.08f), (alphaPct + lift).coerceAtMost(100))
        return GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, base)).apply {
            val r = radiusDp.dp.toFloat()
            if (topRadiusOnly) cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f) else cornerRadius = r
            if (borderPct > 0) setStroke(1.dp.coerceAtLeast(1), withAlpha(Color.WHITE, borderPct))
        }
    }

    fun blend(a: Int, b: Int, t: Float): Int {
        val r = (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt()
        val g = (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt()
        val bl = (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt()
        return Color.rgb(r, g, bl)
    }

    /** 0..100 のぼかし値をウィンドウのぼかし半径(px)に */
    fun blurPx(v: Int): Int = (v.coerceIn(0, 100) / 100f * 64.dp).toInt()

    fun typeface(f: LabelFont): Typeface = when (f) {
        LabelFont.DEFAULT -> Typeface.DEFAULT
        LabelFont.MEDIUM -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
        LabelFont.BOLD -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        LabelFont.CONDENSED -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        LabelFont.SERIF -> Typeface.SERIF
        LabelFont.MONO -> Typeface.MONOSPACE
    }

    fun fontLabel(f: LabelFont): String = when (f) {
        LabelFont.DEFAULT -> "標準"
        LabelFont.MEDIUM -> "ミディアム"
        LabelFont.BOLD -> "太字"
        LabelFont.CONDENSED -> "コンデンス"
        LabelFont.SERIF -> "セリフ"
        LabelFont.MONO -> "等幅"
    }

    fun shapeLabel(s: AppIconShape): String = when (s) {
        AppIconShape.SYSTEM -> "標準"
        AppIconShape.CIRCLE -> "丸"
        AppIconShape.SQUIRCLE -> "スクワークル"
        AppIconShape.ROUNDED -> "角丸"
        AppIconShape.SQUARE -> "四角"
        AppIconShape.TEARDROP -> "しずく"
    }

    /** アプリアイコンをテーマの形に整えたもの */
    fun icon(key: String, src: Drawable): Drawable {
        val shape = theme.iconShape
        if (shape == AppIconShape.SYSTEM || src !is AdaptiveIconDrawable) return src
        return iconCache.getOrPut(key) { ShapedIconDrawable(src, shape) }
    }

    fun shapePath(shape: AppIconShape, r: RectF): Path {
        val p = Path()
        val w = r.width()
        when (shape) {
            AppIconShape.CIRCLE, AppIconShape.SYSTEM -> p.addOval(r, Path.Direction.CW)
            AppIconShape.ROUNDED -> p.addRoundRect(r, w * 0.24f, w * 0.24f, Path.Direction.CW)
            AppIconShape.SQUARE -> p.addRoundRect(r, w * 0.07f, w * 0.07f, Path.Direction.CW)
            AppIconShape.TEARDROP -> {
                val rr = w * 0.5f
                p.addRoundRect(r, floatArrayOf(rr, rr, rr, rr, w * 0.12f, w * 0.12f, rr, rr), Path.Direction.CW)
            }
            AppIconShape.SQUIRCLE -> {
                val l = r.left; val t = r.top; val s = w
                val c = s / 2f
                val m = s * 0.04f
                p.moveTo(l + c, t)
                p.cubicTo(l + s - m, t, l + s, t + m, l + s, t + c)
                p.cubicTo(l + s, t + s - m, l + s - m, t + s, l + c, t + s)
                p.cubicTo(l + m, t + s, l, t + s - m, l, t + c)
                p.cubicTo(l, t + m, l + m, t, l + c, t)
                p.close()
            }
        }
        return p
    }
}

/** アダプティブアイコンを任意の形で切り抜いて描く */
class ShapedIconDrawable(private val src: AdaptiveIconDrawable, private val shape: AppIconShape) : Drawable() {

    private val tmp = Rect()

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val path = Look.shapePath(shape, RectF(b))
        canvas.save()
        canvas.clipPath(path)
        // レイヤーは表示領域の 1.5 倍の大きさ（108dp 中 72dp が見える）
        val ext = b.width() / 4
        tmp.set(b.left - ext, b.top - ext, b.right + ext, b.bottom + ext)
        src.background?.let { it.setBounds(tmp); it.draw(canvas) }
        src.foreground?.let { it.setBounds(tmp); it.draw(canvas) }
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) { src.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { src.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    override fun getIntrinsicWidth(): Int = src.intrinsicWidth
    override fun getIntrinsicHeight(): Int = src.intrinsicHeight
}
