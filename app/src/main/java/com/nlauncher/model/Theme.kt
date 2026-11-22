package com.nlauncher.model

import org.json.JSONObject

/** アプリアイコンの形（SYSTEM = 端末標準のまま） */
enum class AppIconShape { SYSTEM, CIRCLE, SQUIRCLE, ROUNDED, SQUARE, TEARDROP }

enum class LabelFont { DEFAULT, MEDIUM, BOLD, CONDENSED, SERIF, MONO }

/**
 * 見た目の設定一式。色はすべて RGB（不透明）で持ち、透明度は別の 0..100 で持つ。
 * ぼかしは 0..100（実際の半径に換算して使う）。
 */
class Theme(
    var name: String = "グラス",
    var accent: Int = 0xFF8AB4F8.toInt(),

    // 壁紙
    var wallBlur: Int = 0,
    var wallDim: Int = 10,

    // アイコン
    var iconShape: AppIconShape = AppIconShape.SQUIRCLE,
    var iconScale: Int = 100,
    var labelColor: Int = 0xFFFFFFFF.toInt(),
    var labelSize: Int = 12,
    var labelShadow: Boolean = true,
    var labelFont: LabelFont = LabelFont.MEDIUM,

    // アプリ一覧
    var drawerColor: Int = 0xFF0E1020.toInt(),
    var drawerAlpha: Int = 55,
    var drawerBlur: Int = 70,

    // 開いたグループ・メニュー
    var folderColor: Int = 0xFF1A1D2E.toInt(),
    var folderAlpha: Int = 70,
    var folderBlur: Int = 45,
    var folderRadius: Int = 30,

    // ホーム上のパネル
    var panelColor: Int = 0xFFFFFFFF.toInt(),
    var panelAlpha: Int = 14,
    var panelRadius: Int = 26,
    var panelBorder: Int = 22,
) {
    fun copy(): Theme = fromJson(toJson())

    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("accent", accent)
        put("wallBlur", wallBlur); put("wallDim", wallDim)
        put("iconShape", iconShape.name); put("iconScale", iconScale)
        put("labelColor", labelColor); put("labelSize", labelSize)
        put("labelShadow", labelShadow); put("labelFont", labelFont.name)
        put("drawerColor", drawerColor); put("drawerAlpha", drawerAlpha); put("drawerBlur", drawerBlur)
        put("folderColor", folderColor); put("folderAlpha", folderAlpha); put("folderBlur", folderBlur)
        put("folderRadius", folderRadius)
        put("panelColor", panelColor); put("panelAlpha", panelAlpha); put("panelRadius", panelRadius)
        put("panelBorder", panelBorder)
    }

    companion object {
        fun fromJson(o: JSONObject?): Theme {
            val d = Theme()
            if (o == null) return d
            fun i(k: String, def: Int, lo: Int = 0, hi: Int = 100) = o.optInt(k, def).coerceIn(lo, hi)
            fun c(k: String, def: Int) = o.optInt(k, def) or 0xFF000000.toInt()
            return Theme(
                name = o.optString("name", d.name),
                accent = c("accent", d.accent),
                wallBlur = i("wallBlur", d.wallBlur), wallDim = i("wallDim", d.wallDim),
                iconShape = enumOr(o.optString("iconShape"), d.iconShape),
                iconScale = i("iconScale", d.iconScale, 50, 150),
                labelColor = c("labelColor", d.labelColor),
                labelSize = i("labelSize", d.labelSize, 8, 18),
                labelShadow = o.optBoolean("labelShadow", d.labelShadow),
                labelFont = enumOr(o.optString("labelFont"), d.labelFont),
                drawerColor = c("drawerColor", d.drawerColor), drawerAlpha = i("drawerAlpha", d.drawerAlpha),
                drawerBlur = i("drawerBlur", d.drawerBlur),
                folderColor = c("folderColor", d.folderColor), folderAlpha = i("folderAlpha", d.folderAlpha),
                folderBlur = i("folderBlur", d.folderBlur), folderRadius = i("folderRadius", d.folderRadius, 0, 48),
                panelColor = c("panelColor", d.panelColor), panelAlpha = i("panelAlpha", d.panelAlpha),
                panelRadius = i("panelRadius", d.panelRadius, 0, 48), panelBorder = i("panelBorder", d.panelBorder),
            )
        }

        /** ワンタップで全体に適用できるプリセット */
        val presets: List<Theme> by lazy {
            listOf(
                Theme(),
                Theme(
                    name = "ミッドナイト", accent = 0xFF7C9BFF.toInt(), wallBlur = 20, wallDim = 35,
                    iconShape = AppIconShape.CIRCLE, labelColor = 0xFFE6E9FF.toInt(), labelFont = LabelFont.MEDIUM,
                    drawerColor = 0xFF05060D.toInt(), drawerAlpha = 75, drawerBlur = 90,
                    folderColor = 0xFF0B0E1E.toInt(), folderAlpha = 82, folderBlur = 60, folderRadius = 26,
                    panelColor = 0xFF0B0E1E.toInt(), panelAlpha = 45, panelRadius = 22, panelBorder = 12,
                ),
                Theme(
                    name = "ネオン", accent = 0xFF00F5D4.toInt(), wallBlur = 0, wallDim = 45,
                    iconShape = AppIconShape.SQUIRCLE, labelColor = 0xFF00F5D4.toInt(), labelFont = LabelFont.BOLD,
                    drawerColor = 0xFF0A0014.toInt(), drawerAlpha = 70, drawerBlur = 80,
                    folderColor = 0xFF1A0033.toInt(), folderAlpha = 78, folderBlur = 50, folderRadius = 18,
                    panelColor = 0xFFB517FF.toInt(), panelAlpha = 18, panelRadius = 18, panelBorder = 60,
                ),
                Theme(
                    name = "パステル", accent = 0xFFFF8FB1.toInt(), wallBlur = 30, wallDim = 0,
                    iconShape = AppIconShape.ROUNDED, labelColor = 0xFFFFFFFF.toInt(), labelFont = LabelFont.MEDIUM,
                    drawerColor = 0xFFFFD6E8.toInt(), drawerAlpha = 35, drawerBlur = 100,
                    folderColor = 0xFFFFE4F0.toInt(), folderAlpha = 40, folderBlur = 80, folderRadius = 36,
                    panelColor = 0xFFFFFFFF.toInt(), panelAlpha = 28, panelRadius = 32, panelBorder = 40,
                ),
                Theme(
                    name = "サンセット", accent = 0xFFFFB86B.toInt(), wallBlur = 10, wallDim = 15,
                    iconShape = AppIconShape.TEARDROP, labelColor = 0xFFFFF1E0.toInt(), labelFont = LabelFont.CONDENSED,
                    drawerColor = 0xFF2B1020.toInt(), drawerAlpha = 60, drawerBlur = 75,
                    folderColor = 0xFF3A1428.toInt(), folderAlpha = 72, folderBlur = 50, folderRadius = 28,
                    panelColor = 0xFFFF7A59.toInt(), panelAlpha = 16, panelRadius = 26, panelBorder = 30,
                ),
                Theme(
                    name = "ミニマル", accent = 0xFFFFFFFF.toInt(), wallBlur = 0, wallDim = 0,
                    iconShape = AppIconShape.SYSTEM, iconScale = 90, labelColor = 0xFFFFFFFF.toInt(), labelSize = 11,
                    labelFont = LabelFont.DEFAULT,
                    drawerColor = 0xFF000000.toInt(), drawerAlpha = 85, drawerBlur = 0,
                    folderColor = 0xFF000000.toInt(), folderAlpha = 85, folderBlur = 0, folderRadius = 14,
                    panelColor = 0xFF000000.toInt(), panelAlpha = 0, panelRadius = 0, panelBorder = 0,
                ),
                Theme(
                    name = "フロスト", accent = 0xFF1E88E5.toInt(), wallBlur = 50, wallDim = 0,
                    iconShape = AppIconShape.SQUIRCLE, labelColor = 0xFFFFFFFF.toInt(), labelFont = LabelFont.MEDIUM,
                    drawerColor = 0xFFFFFFFF.toInt(), drawerAlpha = 22, drawerBlur = 100,
                    folderColor = 0xFFFFFFFF.toInt(), folderAlpha = 26, folderBlur = 100, folderRadius = 34,
                    panelColor = 0xFFFFFFFF.toInt(), panelAlpha = 20, panelRadius = 30, panelBorder = 45,
                ),
            )
        }
    }
}
