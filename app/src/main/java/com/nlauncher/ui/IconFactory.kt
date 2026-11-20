package com.nlauncher.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.LruCache
import com.nlauncher.model.Group
import com.nlauncher.model.GroupIcon
import com.nlauncher.model.IconShape
import com.nlauncher.model.IconStyle
import java.io.File
import kotlin.math.min

/** グループアイコンを描画する */
object IconFactory {

    private val imageCache = LruCache<String, Bitmap>(16)

    fun shapePath(shape: IconShape, size: Float): Path = Path().apply {
        when (shape) {
            IconShape.CIRCLE -> addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CW)
            IconShape.ROUNDED -> addRoundRect(RectF(0f, 0f, size, size), size * 0.24f, size * 0.24f, Path.Direction.CW)
            IconShape.SQUARE -> addRoundRect(RectF(0f, 0f, size, size), size * 0.06f, size * 0.06f, Path.Direction.CW)
            IconShape.SQUIRCLE -> {
                // スーパー楕円に近い形
                val c = size / 2f
                val k = size * 0.5f
                val m = size * 0.04f
                moveTo(c, 0f)
                cubicTo(c + k - m, 0f, size, m, size, c)
                cubicTo(size, c + k - m, c + k - m, size, c, size)
                cubicTo(m, size, 0f, c + k - m, 0f, c)
                cubicTo(0f, m, m, 0f, c, 0f)
                close()
            }
        }
    }

    fun loadImage(path: String?): Bitmap? {
        if (path == null) return null
        imageCache.get(path)?.let { return it }
        val f = File(path)
        if (!f.exists()) return null
        val bmp = BitmapFactory.decodeFile(path) ?: return null
        imageCache.put(path, bmp)
        return bmp
    }

    fun invalidateImage(path: String?) {
        if (path != null) imageCache.remove(path)
    }

    /**
     * グループのアイコンを生成する
     * @param appIcons PREVIEW スタイルで使うアプリアイコン（先頭から最大4個）
     */
    fun render(icon: GroupIcon, group: Group?, appIcons: List<Drawable>, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val s = size.toFloat()
        val path = shapePath(icon.shape, s)
        canvas.save()
        canvas.clipPath(path)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = icon.bgColor }
        canvas.drawPath(path, bgPaint)

        when (icon.style) {
            IconStyle.PREVIEW -> drawPreview(canvas, appIcons, s)
            IconStyle.TEXT -> drawText(canvas, icon.text.ifEmpty { group?.name?.take(1) ?: "?" }, icon.fgColor, s)
            IconStyle.IMAGE -> {
                val img = loadImage(icon.imagePath)
                if (img != null) {
                    val side = min(img.width, img.height)
                    val src = Rect((img.width - side) / 2, (img.height - side) / 2, (img.width + side) / 2, (img.height + side) / 2)
                    canvas.drawBitmap(img, src, RectF(0f, 0f, s, s), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
                } else {
                    drawPreview(canvas, appIcons, s)
                }
            }
        }
        canvas.restore()

        // 縁取り（透明背景でも形が分かるように）
        if (Color.alpha(icon.bgColor) < 0x40 && icon.style != IconStyle.IMAGE) {
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = s * 0.02f
                color = 0x66FFFFFF
            }
            canvas.drawPath(path, stroke)
        }
        return bmp
    }

    private fun drawPreview(canvas: Canvas, icons: List<Drawable>, s: Float) {
        if (icons.isEmpty()) {
            drawText(canvas, "+", Color.WHITE, s)
            return
        }
        val n = if (icons.size <= 1) 1 else if (icons.size <= 4) 2 else 3
        val pad = s * (if (n == 1) 0.18f else 0.14f)
        val gap = s * 0.05f
        val cell = (s - pad * 2 - gap * (n - 1)) / n
        icons.take(n * n).forEachIndexed { i, d ->
            val cx = i % n
            val cy = i / n
            val l = pad + cx * (cell + gap)
            val t = pad + cy * (cell + gap)
            val old = d.copyBounds()
            d.setBounds(l.toInt(), t.toInt(), (l + cell).toInt(), (t + cell).toInt())
            d.draw(canvas)
            d.setBounds(old)
        }
    }

    private fun drawText(canvas: Canvas, text: String, color: Int, s: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = s * 0.5f
        }
        val maxW = s * 0.78f
        val w = p.measureText(text)
        if (w > maxW) p.textSize = p.textSize * maxW / w
        val fm = p.fontMetrics
        val y = s / 2f - (fm.ascent + fm.descent) / 2f
        canvas.drawText(text, s / 2f, y, p)
    }
}
