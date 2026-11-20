package com.nlauncher.model

import android.graphics.Point
import org.json.JSONArray
import org.json.JSONObject

enum class ItemType { APP, GROUP, WIDGET }

/** グループの表示形式。FOLDER = アイコン表示（タップで開く）、PANEL = ホーム上でスクロールできるパネル */
enum class GroupMode { FOLDER, PANEL }

enum class IconStyle { PREVIEW, TEXT, IMAGE }

enum class IconShape { CIRCLE, ROUNDED, SQUIRCLE, SQUARE }

class GroupIcon(
    var style: IconStyle = IconStyle.PREVIEW,
    var shape: IconShape = IconShape.ROUNDED,
    var bgColor: Int = 0x66FFFFFF,
    var fgColor: Int = 0xFFFFFFFF.toInt(),
    var text: String = "",
    var imagePath: String? = null,
) {
    fun copy() = GroupIcon(style, shape, bgColor, fgColor, text, imagePath)

    fun toJson(): JSONObject = JSONObject().apply {
        put("style", style.name)
        put("shape", shape.name)
        put("bg", bgColor)
        put("fg", fgColor)
        put("text", text)
        imagePath?.let { put("image", it) }
    }

    companion object {
        fun fromJson(o: JSONObject?): GroupIcon {
            if (o == null) return GroupIcon()
            return GroupIcon(
                style = enumOr(o.optString("style"), IconStyle.PREVIEW),
                shape = enumOr(o.optString("shape"), IconShape.ROUNDED),
                bgColor = o.optInt("bg", 0x66FFFFFF),
                fgColor = o.optInt("fg", 0xFFFFFFFF.toInt()),
                text = o.optString("text", ""),
                imagePath = if (o.has("image")) o.optString("image") else null,
            )
        }
    }
}

class Group(
    var name: String,
    val apps: MutableList<String> = mutableListOf(),
    var icon: GroupIcon = GroupIcon(),
    var mode: GroupMode = GroupMode.FOLDER,
    var scrollHorizontal: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("apps", JSONArray(apps))
        put("icon", icon.toJson())
        put("mode", mode.name)
        put("hscroll", scrollHorizontal)
    }

    companion object {
        fun fromJson(o: JSONObject): Group {
            val arr = o.optJSONArray("apps") ?: JSONArray()
            val apps = MutableList(arr.length()) { arr.getString(it) }
            return Group(
                name = o.optString("name", "グループ"),
                apps = apps,
                icon = GroupIcon.fromJson(o.optJSONObject("icon")),
                mode = enumOr(o.optString("mode"), GroupMode.FOLDER),
                scrollHorizontal = o.optBoolean("hscroll", false),
            )
        }
    }
}

class HomeItem(
    val id: Long,
    val type: ItemType,
    var x: Int = 0,
    var y: Int = 0,
    var w: Int = 1,
    var h: Int = 1,
    var app: String? = null,
    var group: Group? = null,
    var widgetId: Int = -1,
) {
    fun overlaps(ox: Int, oy: Int, ow: Int, oh: Int): Boolean =
        x < ox + ow && ox < x + w && y < oy + oh && oy < y + h

    fun contains(cx: Int, cy: Int): Boolean = cx >= x && cx < x + w && cy >= y && cy < y + h

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", type.name)
        put("x", x); put("y", y); put("w", w); put("h", h)
        app?.let { put("app", it) }
        group?.let { put("group", it.toJson()) }
        put("widgetId", widgetId)
    }

    companion object {
        fun fromJson(o: JSONObject): HomeItem? {
            val type = runCatching { ItemType.valueOf(o.getString("type")) }.getOrNull() ?: return null
            return HomeItem(
                id = o.getLong("id"),
                type = type,
                x = o.optInt("x"), y = o.optInt("y"),
                w = o.optInt("w", 1).coerceAtLeast(1), h = o.optInt("h", 1).coerceAtLeast(1),
                app = if (o.has("app")) o.getString("app") else null,
                group = o.optJSONObject("group")?.let { Group.fromJson(it) },
                widgetId = o.optInt("widgetId", -1),
            )
        }
    }
}

class Page(val id: Long, val items: MutableList<HomeItem> = mutableListOf()) {

    fun isFree(x: Int, y: Int, w: Int, h: Int, cols: Int, rows: Int, ignore: HomeItem? = null): Boolean {
        if (x < 0 || y < 0 || w < 1 || h < 1 || x + w > cols || y + h > rows) return false
        return items.none { it !== ignore && it.overlaps(x, y, w, h) }
    }

    fun findFree(w: Int, h: Int, cols: Int, rows: Int): Point? {
        for (y in 0..rows - h) for (x in 0..cols - w) {
            if (isFree(x, y, w, h, cols, rows)) return Point(x, y)
        }
        return null
    }

    fun itemAt(cx: Int, cy: Int, ignore: HomeItem? = null): HomeItem? =
        items.firstOrNull { it !== ignore && it.contains(cx, cy) }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("items", JSONArray().also { a -> items.forEach { a.put(it.toJson()) } })
    }

    companion object {
        fun fromJson(o: JSONObject): Page {
            val arr = o.optJSONArray("items") ?: JSONArray()
            val list = mutableListOf<HomeItem>()
            for (i in 0 until arr.length()) HomeItem.fromJson(arr.getJSONObject(i))?.let { list += it }
            return Page(o.getLong("id"), list)
        }
    }
}

class Settings(
    var cols: Int = 5,
    var rows: Int = 6,
    var showLabels: Boolean = true,
    var drawerCols: Int = 5,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("cols", cols); put("rows", rows); put("labels", showLabels); put("drawerCols", drawerCols)
    }

    companion object {
        fun fromJson(o: JSONObject?): Settings {
            if (o == null) return Settings()
            return Settings(
                cols = o.optInt("cols", 5).coerceIn(2, 12),
                rows = o.optInt("rows", 6).coerceIn(2, 16),
                showLabels = o.optBoolean("labels", true),
                drawerCols = o.optInt("drawerCols", 5).coerceIn(3, 8),
            )
        }
    }
}

class Workspace(
    val pages: MutableList<Page> = mutableListOf(),
    var settings: Settings = Settings(),
    private var nextId: Long = 1,
) {
    fun newId(): Long = nextId++

    fun newPage(): Page = Page(newId())

    fun pageOf(item: HomeItem): Page? = pages.firstOrNull { p -> p.items.any { it === item } }

    fun allItems(): Sequence<HomeItem> = pages.asSequence().flatMap { it.items.asSequence() }

    /** 全ページの中から w x h が置ける最初の場所を探す。見つからなければ新ページを作る。 */
    fun placeAnywhere(item: HomeItem, preferPage: Int) {
        val cols = settings.cols
        val rows = settings.rows
        item.w = item.w.coerceIn(1, cols)
        item.h = item.h.coerceIn(1, rows)
        val order = pages.indices.sortedBy { if (it >= preferPage) it - preferPage else it + pages.size }
        for (i in order) {
            val p = pages[i].findFree(item.w, item.h, cols, rows)
            if (p != null) {
                item.x = p.x; item.y = p.y
                pages[i].items += item
                return
            }
        }
        val page = newPage()
        pages += page
        item.x = 0; item.y = 0
        page.items += item
    }

    /** グリッドサイズ変更後などに、はみ出し・重なりを解消する */
    fun normalize() {
        if (pages.isEmpty()) pages += newPage()
        val cols = settings.cols
        val rows = settings.rows
        val overflow = mutableListOf<HomeItem>()
        for (page in pages) {
            val sorted = page.items.sortedWith(compareBy({ it.y }, { it.x }))
            page.items.clear()
            for (item in sorted) {
                item.w = item.w.coerceIn(1, cols)
                item.h = item.h.coerceIn(1, rows)
                if (page.isFree(item.x, item.y, item.w, item.h, cols, rows)) {
                    page.items += item
                    continue
                }
                val p = page.findFree(item.w, item.h, cols, rows)
                if (p != null) {
                    item.x = p.x; item.y = p.y
                    page.items += item
                } else {
                    overflow += item
                }
            }
        }
        for (item in overflow) placeAnywhere(item, pages.size - 1)
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("version", 1)
        put("nextId", nextId)
        put("settings", settings.toJson())
        put("pages", JSONArray().also { a -> pages.forEach { a.put(it.toJson()) } })
    }

    companion object {
        fun fromJson(o: JSONObject): Workspace {
            val arr = o.optJSONArray("pages") ?: JSONArray()
            val pages = MutableList(arr.length()) { Page.fromJson(arr.getJSONObject(it)) }
            var maxId = 0L
            pages.forEach { p ->
                maxId = maxOf(maxId, p.id)
                p.items.forEach { maxId = maxOf(maxId, it.id) }
            }
            val ws = Workspace(pages, Settings.fromJson(o.optJSONObject("settings")), maxOf(o.optLong("nextId", 1), maxId + 1))
            ws.normalize()
            return ws
        }
    }
}

inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
    runCatching { enumValueOf<T>(name ?: "") }.getOrDefault(default)
