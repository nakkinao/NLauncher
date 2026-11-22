package com.nlauncher

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Point
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.util.SizeF
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.nlauncher.data.AppInfo
import com.nlauncher.data.AppsRepository
import com.nlauncher.data.WorkspaceStore
import com.nlauncher.model.Group
import com.nlauncher.model.GroupMode
import com.nlauncher.model.HomeItem
import com.nlauncher.model.ItemType
import com.nlauncher.model.Page
import com.nlauncher.model.Workspace
import com.nlauncher.model.Theme
import com.nlauncher.model.IconShape
import com.nlauncher.model.IconStyle
import com.nlauncher.model.Settings
import com.nlauncher.ui.GlassMenu
import com.nlauncher.ui.Look
import com.nlauncher.ui.SettingsSheet
import com.nlauncher.ui.showStyled
import android.content.ClipData
import android.content.ClipboardManager
import android.view.WindowManager
import com.nlauncher.ui.CellLayout
import com.nlauncher.ui.DragLayer
import com.nlauncher.ui.DrawerView
import com.nlauncher.ui.FolderView
import com.nlauncher.ui.GroupIconEditor
import com.nlauncher.ui.GroupPanelView
import com.nlauncher.ui.IconFactory
import com.nlauncher.ui.IconView
import com.nlauncher.ui.LauncherWidgetHost
import com.nlauncher.ui.LauncherWidgetHostView
import com.nlauncher.ui.PageIndicator
import com.nlauncher.ui.ResizeFrame
import com.nlauncher.ui.WorkspaceView
import com.nlauncher.ui.dp
import com.nlauncher.ui.roundedBg
import com.nlauncher.ui.selectableBorderless
import com.nlauncher.ui.showAppPicker
import com.nlauncher.ui.showTextInput
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.min

class MainActivity : Activity(), DragLayer.DragHandler, FolderView.Callbacks, SettingsSheet.Host {

    private lateinit var store: WorkspaceStore
    private lateinit var ws: Workspace
    private lateinit var apps: AppsRepository
    private lateinit var awm: AppWidgetManager
    private lateinit var host: LauncherWidgetHost

    private lateinit var dragLayer: DragLayer
    private lateinit var content: LinearLayout
    private lateinit var workspace: WorkspaceView
    private lateinit var indicator: PageIndicator
    private lateinit var drawer: DrawerView
    private lateinit var folder: FolderView

    private val main = Handler(Looper.getMainLooper())
    private val widgetViews = HashMap<Int, View>()
    private var firstRun = false
    private var touchSlop = 0

    private var openFolderItem: HomeItem? = null
    private var resizeFrame: ResizeFrame? = null
    private var iconEditor: GroupIconEditor? = null
    private var settingsSheet: SettingsSheet? = null
    private var glassMenu: GlassMenu? = null
    private lateinit var handle: TextView
    private var drawerP = 0f
    private var folderP = 0f
    private var currentBlur = -1

    private var pendingWidgetId = -1
    private var pendingCell: Point? = null
    private var pendingPage = 0
    private var lastLongPressCell: Point? = null

    // ---------------------------------------------------------------- lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        store = WorkspaceStore(this)
        val loaded = store.load()
        firstRun = loaded == null
        ws = loaded ?: Workspace().also { it.pages += it.newPage(); it.pages += it.newPage() }
        if (ws.pages.isEmpty()) ws.pages += ws.newPage()
        Look.theme = ws.theme

        awm = AppWidgetManager.getInstance(this)
        host = LauncherWidgetHost(applicationContext, HOST_ID)
        apps = AppsRepository(applicationContext)

        savedInstanceState?.let {
            pendingWidgetId = it.getInt("pendingWidgetId", -1)
            pendingPage = it.getInt("pendingPage", 0)
        }

        buildUi()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        apps.addListener { onAppsChanged() }
        apps.reload()
        renderAll()
        updateBackdrop()
        workspace.setPageImmediate(savedInstanceState?.getInt("page", 0) ?: 0)
    }

    override fun onStart() {
        super.onStart()
        try { host.startListening() } catch (e: Exception) { /* ignore */ }
    }

    override fun onStop() {
        super.onStop()
        try { host.stopListening() } catch (e: Exception) { /* ignore */ }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("pendingWidgetId", pendingWidgetId)
        outState.putInt("pendingPage", pendingPage)
        outState.putInt("page", workspace.currentPage)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            if (drag != null) return
            val closed = closeOverlays()
            if (!closed && hasWindowFocus()) workspace.snapToPage(0)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        closeOverlays()
    }

    private fun closeOverlays(): Boolean {
        var closed = false
        glassMenu?.let { it.dismiss(); closed = true }
        settingsSheet?.let { it.finish(); closed = true }
        resizeFrame?.let { finishResize(); closed = true }
        if (folder.isOpen) { folder.close(); closed = true }
        if (drawer.isOpen) { drawer.close(); closed = true }
        return closed
    }

    // ---------------------------------------------------------------- UI

    private fun buildUi() {
        dragLayer = DragLayer(this)
        dragLayer.dragHandler = this

        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        workspace = WorkspaceView(this)
        content.addView(workspace, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        indicator = PageIndicator(this)
        content.addView(indicator, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 22.dp))

        handle = TextView(this).apply {
            text = "︿"
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            setShadowLayer(4f, 0f, 1f, 0xAA000000.toInt())
            selectableBorderless(this@MainActivity)
            contentDescription = "すべてのアプリ"
            setOnClickListener { openDrawer() }
            setOnLongClickListener { showHomeMenu(); true }
        }
        content.addView(handle, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 40.dp))
        dragLayer.addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        drawer = DrawerView(
            this, ws.settings.drawerCols,
            onAppClick = { app, v -> apps.launch(app.key, v); main.postDelayed({ drawer.close(false) }, 300) },
            onAppLongClick = { app, v -> startDrawerDrag(app, v); true },
        )
        drawer.onCloseRequest = { drawer.close() }
        drawer.onProgress = { drawerP = it; updateBackdrop() }
        dragLayer.addView(drawer, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        folder = FolderView(this, this)
        folder.onProgress = { folderP = it; updateBackdrop() }
        dragLayer.addView(folder, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        setContentView(dragLayer)

        ViewCompat.setOnApplyWindowInsetsListener(dragLayer) { _, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            content.setPadding(b.left, b.top, b.right, b.bottom)
            drawer.setPadding(b.left, b.top, b.right, b.bottom)
            folder.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }

        workspace.onPageChanged = { /* indicator follows scroll */ }
        workspace.onScrollProgress = { indicator.progress = it }
        workspace.onSwipeUp = { openDrawer() }
        workspace.onSwipeDown = { expandNotifications() }
        indicator.onPageTap = { workspace.snapToPage(it) }
    }

    private fun openDrawer() {
        if (drag != null) return
        drawer.open()
    }

    private fun expandNotifications() {
        try {
            @Suppress("WrongConstant")
            val sb = getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sb)
        } catch (e: Exception) { /* ignore */ }
    }

    private fun newCellLayout(): CellLayout = CellLayout(this).apply {
        cols = ws.settings.cols
        rows = ws.settings.rows
        isLongClickable = true
        setOnLongClickListener { cl ->
            val c = cl as CellLayout
            lastLongPressCell = c.cellAt(dragLayer.lastX - dragLayer.locationOf(c)[0], dragLayer.lastY - dragLayer.locationOf(c)[1])
            c.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            showHomeMenu()
            true
        }
    }

    private fun renderAll() {
        while (workspace.childCount < ws.pages.size) workspace.addView(newCellLayout())
        while (workspace.childCount > ws.pages.size) {
            val cl = workspace.getChildAt(workspace.childCount - 1) as CellLayout
            cl.removeAllViews()
            workspace.removeViewAt(workspace.childCount - 1)
        }
        for (i in ws.pages.indices) renderPage(i)
        indicator.count = ws.pages.size
        if (workspace.currentPage >= ws.pages.size) workspace.setPageImmediate(ws.pages.size - 1)
        indicator.progress = workspace.scrollX.toFloat() / workspace.width.coerceAtLeast(1)
    }

    private fun renderPage(index: Int) {
        val cl = workspace.pageAt(index) ?: return
        val page = ws.pages[index]
        cl.cols = ws.settings.cols
        cl.rows = ws.settings.rows
        cl.removeAllViews()
        for (item in page.items) {
            val v = createItemView(item, cl)
            v.tag = item
            cl.addItemView(v, item.x, item.y, item.w, item.h)
        }
    }

    private fun createItemView(item: HomeItem, cl: CellLayout): View {
        val labels = ws.settings.showLabels
        return when (item.type) {
            ItemType.APP -> {
                val app = apps.get(item.app)
                IconView(this).apply {
                    icon = app?.let { Look.icon(it.key, it.icon) } ?: packageManager.defaultActivityIcon
                    label = app?.label ?: ""
                    showLabel = labels
                    dimmed = app == null && apps.loaded
                    setOnClickListener { item.app?.let { apps.launch(it, this) } }
                    setOnLongClickListener { startHomeDrag(item, this); true }
                }
            }
            ItemType.GROUP -> {
                val g = item.group ?: Group("グループ").also { item.group = it }
                if (g.mode == GroupMode.PANEL) {
                    GroupPanelView(
                        this, { cl.cellWidth }, { cl.cellHeight }, labels,
                        onAppClick = { app, v -> apps.launch(app.key, v) },
                        onAppLongClick = { app, v -> showGroupAppMenu(item, app, v); true },
                    ).also { panel ->
                        panel.bind(g, groupApps(g), groupBitmap(g, 64))
                        panel.header.setOnClickListener { openFolder(item) }
                        panel.header.setOnLongClickListener { startHomeDrag(item, panel); true }
                    }
                } else {
                    IconView(this).apply {
                        icon = BitmapDrawable(resources, groupBitmap(g, 192))
                        label = g.name
                        showLabel = labels
                        setOnClickListener { openFolder(item) }
                        setOnLongClickListener { startHomeDrag(item, this); true }
                    }
                }
            }
            ItemType.WIDGET -> widgetView(item)
        }
    }

    private fun groupApps(g: Group): List<AppInfo> = g.apps.mapNotNull { apps.get(it) }

    private fun groupIcons(g: Group): List<Drawable> = groupApps(g).take(9).map { Look.icon(it.key, it.icon) }

    private fun groupBitmap(g: Group, size: Int): Bitmap = IconFactory.render(g.icon, g, groupIcons(g), size)

    // ---------------------------------------------------------------- widgets

    private fun widgetView(item: HomeItem): View {
        widgetViews[item.widgetId]?.let { v ->
            if (v is LauncherWidgetHostView) v.onLongPress = { startHomeDrag(item, v) }
            return v
        }
        val info = try { awm.getAppWidgetInfo(item.widgetId) } catch (e: Exception) { null }
        if (info == null) {
            return TextView(this).apply {
                text = "ウィジェットを読み込めません\n（長押しで削除）"
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                background = roundedBg(0x55000000, 16)
                isLongClickable = true
                setOnLongClickListener { startHomeDrag(item, this); true }
            }
        }
        val v = host.createView(this, item.widgetId, info) as LauncherWidgetHostView
        v.onLongPress = { startHomeDrag(item, v) }
        v.addOnLayoutChangeListener { view, l, t, r, b, ol, ot, or_, ob ->
            if (r - l != or_ - ol || b - t != ob - ot) updateWidgetSize(view as LauncherWidgetHostView, r - l, b - t)
        }
        widgetViews[item.widgetId] = v
        return v
    }

    @Suppress("DEPRECATION")
    private fun updateWidgetSize(v: LauncherWidgetHostView, wPx: Int, hPx: Int) {
        if (wPx <= 0 || hPx <= 0) return
        val d = resources.displayMetrics.density
        val wDp = wPx / d
        val hDp = hPx / d
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                v.updateAppWidgetSize(Bundle(), listOf(SizeF(wDp, hDp)))
            } else {
                v.updateAppWidgetSize(null, wDp.toInt(), hDp.toInt(), wDp.toInt(), hDp.toInt())
            }
        } catch (e: Exception) { /* ignore */ }
    }

    private fun pickWidget(cell: Point?) {
        pendingCell = cell
        pendingPage = workspace.currentPage
        @Suppress("DEPRECATION")
        startActivityForResult(Intent(this, WidgetPickerActivity::class.java), REQ_PICK_WIDGET)
    }

    private fun onWidgetPicked(provider: ComponentName, user: UserHandle) {
        val id = host.allocateAppWidgetId()
        pendingWidgetId = id
        val ok = try {
            awm.bindAppWidgetIdIfAllowed(id, user, provider, null)
        } catch (e: Exception) { false }
        if (ok) {
            configureOrAdd(id)
        } else {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, user)
            try {
                @Suppress("DEPRECATION")
                startActivityForResult(intent, REQ_BIND_WIDGET)
            } catch (e: Exception) {
                host.deleteAppWidgetId(id)
                pendingWidgetId = -1
                toast("ウィジェットを追加できませんでした")
            }
        }
    }

    private fun needsConfigure(info: AppWidgetProviderInfo): Boolean {
        if (info.configure == null) return false
        if (Build.VERSION.SDK_INT >= 31 &&
            (info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL) != 0
        ) return false
        return true
    }

    private fun configureOrAdd(id: Int) {
        val info = awm.getAppWidgetInfo(id)
        if (info == null) {
            host.deleteAppWidgetId(id)
            pendingWidgetId = -1
            return
        }
        if (needsConfigure(info)) {
            try {
                host.startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CONFIG_WIDGET, null)
                return
            } catch (e: Exception) {
                // 設定画面を開けない場合はそのまま追加
            }
        }
        addWidget(id)
    }

    private fun addWidget(id: Int) {
        pendingWidgetId = -1
        val info = awm.getAppWidgetInfo(id) ?: run { host.deleteAppWidgetId(id); return }
        val cols = ws.settings.cols
        val rows = ws.settings.rows
        val cl = workspace.currentCellLayout
        val cw = cl?.cellWidth?.takeIf { it > 0 } ?: (resources.displayMetrics.widthPixels / cols.toFloat())
        val ch = cl?.cellHeight?.takeIf { it > 0 } ?: (resources.displayMetrics.heightPixels * 0.8f / rows)
        var w = ceil(info.minWidth / cw).toInt()
        var h = ceil(info.minHeight / ch).toInt()
        if (Build.VERSION.SDK_INT >= 31 && info.targetCellWidth > 0 && info.targetCellHeight > 0) {
            w = info.targetCellWidth
            h = info.targetCellHeight
        }
        w = w.coerceIn(1, cols)
        h = h.coerceIn(1, rows)
        val item = HomeItem(ws.newId(), ItemType.WIDGET, w = w, h = h, widgetId = id)
        placeItem(item, pendingPage.coerceIn(0, ws.pages.size - 1), pendingCell)
        pendingCell = null
        save()
        renderAll()
        val page = ws.pages.indexOf(ws.pageOf(item))
        if (page >= 0) workspace.snapToPage(page)
    }

    /** 指定ページ・セルに置けなければ空いている場所に置く */
    private fun placeItem(item: HomeItem, pageIndex: Int, cell: Point?) {
        val page = ws.pages[pageIndex]
        val cols = ws.settings.cols
        val rows = ws.settings.rows
        if (cell != null) {
            val x = cell.x.coerceIn(0, (cols - item.w).coerceAtLeast(0))
            val y = cell.y.coerceIn(0, (rows - item.h).coerceAtLeast(0))
            if (page.isFree(x, y, item.w, item.h, cols, rows)) {
                item.x = x; item.y = y
                page.items += item
                return
            }
        }
        ws.placeAnywhere(item, pageIndex)
    }

    private fun deleteItem(item: HomeItem) {
        val page = ws.pageOf(item) ?: return
        page.items.remove(item)
        if (item.type == ItemType.WIDGET) {
            widgetViews.remove(item.widgetId)
            try { host.deleteAppWidgetId(item.widgetId) } catch (e: Exception) { /* ignore */ }
        }
        if (openFolderItem === item) folder.close()
        save()
        renderAll()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_PICK_WIDGET -> if (resultCode == RESULT_OK && data != null) {
                @Suppress("DEPRECATION")
                val provider = data.getParcelableExtra<ComponentName>(WidgetPickerActivity.EXTRA_PROVIDER)
                @Suppress("DEPRECATION")
                val user = data.getParcelableExtra<UserHandle>(WidgetPickerActivity.EXTRA_USER)
                    ?: android.os.Process.myUserHandle()
                if (provider != null) onWidgetPicked(provider, user)
            }
            REQ_BIND_WIDGET -> {
                val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId) ?: pendingWidgetId
                if (resultCode == RESULT_OK && id != -1) configureOrAdd(id)
                else if (id != -1) { host.deleteAppWidgetId(id); pendingWidgetId = -1 }
            }
            REQ_CONFIG_WIDGET -> {
                val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId) ?: pendingWidgetId
                if (id == -1) return
                if (resultCode == RESULT_OK) addWidget(id)
                else { host.deleteAppWidgetId(id); pendingWidgetId = -1 }
            }
            REQ_RECONFIG_WIDGET -> renderAll()
            REQ_PICK_IMAGE -> if (resultCode == RESULT_OK) {
                val uri = data?.data ?: return
                val path = importImage(uri)
                if (path != null) iconEditor?.setImage(path) else toast("画像を読み込めませんでした")
            }
        }
    }

    // ---------------------------------------------------------------- apps

    private fun onAppsChanged() {
        drawer.setApps(apps.apps)
        pruneUninstalled()
        if (firstRun && apps.apps.isNotEmpty()) {
            firstRun = false
            populateDefault()
        }
        renderAll()
        refreshFolder()
    }

    private fun isPackageGone(key: String?): Boolean {
        if (key == null) return true
        val pkg = key.substringBefore('/')
        return try {
            packageManager.getApplicationInfo(pkg, 0)
            false
        } catch (e: Exception) {
            true
        }
    }

    private fun pruneUninstalled() {
        if (!apps.loaded || apps.apps.isEmpty()) return
        var changed = false
        for (page in ws.pages) {
            val iter = page.items.iterator()
            while (iter.hasNext()) {
                val item = iter.next()
                if (item.type == ItemType.APP && apps.get(item.app) == null && isPackageGone(item.app)) {
                    iter.remove(); changed = true
                }
                val g = item.group
                if (g != null && g.apps.removeAll { k -> apps.get(k) == null && isPackageGone(k) }) changed = true
            }
        }
        if (changed) save()
    }

    /** 初回起動時のサンプル配置 */
    private fun populateDefault() {
        val cols = ws.settings.cols
        val rows = ws.settings.rows
        val page = ws.pages[0]
        val list = apps.apps
        val panel = Group("アプリ", list.take(16).map { it.key }.toMutableList(), mode = GroupMode.PANEL, scrollHorizontal = true)
        page.items += HomeItem(ws.newId(), ItemType.GROUP, 0, 0, cols, 2, group = panel)
        list.drop(16).take(cols).forEachIndexed { i, a ->
            page.items += HomeItem(ws.newId(), ItemType.APP, i, rows - 1, app = a.key)
        }
        save()
        toast("長押しでメニュー / 上スワイプでアプリ一覧")
    }

    // ---------------------------------------------------------------- menus

    private fun menu(title: String?, entries: List<Pair<String, () -> Unit>>) {
        glassMenu?.dismiss()
        val m = GlassMenu(this, title, entries, dragLayer.lastX, dragLayer.lastY) { glassMenu = null }
        glassMenu = m
        m.show(dragLayer)
    }

    private fun showHomeMenu() {
        if (drag != null) return
        val cell = lastLongPressCell
        lastLongPressCell = null
        val cur = workspace.currentPage
        val entries = mutableListOf<Pair<String, () -> Unit>>(
            "デザイン・設定" to { openSettings() },
            "ウィジェットを追加" to { pickWidget(cell) },
            "グループを作成" to { createGroup(cell) },
            "ページを追加" to {
                ws.pages.add(cur + 1, ws.newPage())
                save(); renderAll()
                workspace.snapToPage(cur + 1)
            },
        )
        if (ws.pages.size > 1) {
            if (cur > 0) entries += "このページを左へ移動" to { movePage(cur, cur - 1) }
            if (cur < ws.pages.size - 1) entries += "このページを右へ移動" to { movePage(cur, cur + 1) }
            entries += "このページを削除" to { deletePage(cur) }
        }
        entries += "壁紙を変更" to {
            try {
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "壁紙を選択"))
            } catch (e: Exception) { toast("壁紙アプリが見つかりません") }
        }
        menu("ページ ${cur + 1} / ${ws.pages.size}", entries)
    }

    private fun movePage(from: Int, to: Int) {
        val p = ws.pages.removeAt(from)
        ws.pages.add(to, p)
        save(); renderAll()
        workspace.setPageImmediate(to)
    }

    private fun deletePage(index: Int) {
        val page = ws.pages[index]
        val doDelete = {
            page.items.filter { it.type == ItemType.WIDGET }.forEach {
                widgetViews.remove(it.widgetId)
                try { host.deleteAppWidgetId(it.widgetId) } catch (e: Exception) { /* ignore */ }
            }
            ws.pages.removeAt(index)
            if (ws.pages.isEmpty()) ws.pages += ws.newPage()
            save(); renderAll()
            workspace.setPageImmediate(index.coerceAtMost(ws.pages.size - 1))
        }
        if (page.items.isEmpty()) doDelete()
        else AlertDialog.Builder(this)
            .setMessage("このページのアイテム（${page.items.size}個）も削除されます。よろしいですか？")
            .setPositiveButton("削除") { _, _ -> doDelete() }
            .setNegativeButton("キャンセル", null)
            .showStyled()
    }

    private fun createGroup(cell: Point?, initial: List<String> = emptyList()) {
        showAppPicker(this, "グループに入れるアプリ", apps.apps, initial) { keys ->
            val g = Group("グループ", keys.toMutableList())
            val item = HomeItem(ws.newId(), ItemType.GROUP, group = g)
            placeItem(item, workspace.currentPage, cell)
            save(); renderAll()
            val p = ws.pages.indexOf(ws.pageOf(item))
            if (p >= 0 && p != workspace.currentPage) workspace.snapToPage(p)
            openFolder(item)
        }
    }

    private fun showItemMenu(item: HomeItem, view: View) {
        when (item.type) {
            ItemType.APP -> {
                val key = item.app ?: return
                menu(apps.get(key)?.label, listOf(
                    "グループにする" to { convertAppToGroup(item) },
                    "アプリ情報" to { apps.openAppInfo(key) },
                    "アンインストール" to { apps.uninstall(key) },
                    "ホームから削除" to { deleteItem(item) },
                ))
            }
            ItemType.WIDGET -> {
                val entries = mutableListOf<Pair<String, () -> Unit>>(
                    "サイズ変更" to { startResize(item) },
                )
                val info = try { awm.getAppWidgetInfo(item.widgetId) } catch (e: Exception) { null }
                if (info?.configure != null) entries += "ウィジェットを再設定" to {
                    try {
                        host.startAppWidgetConfigureActivityForResult(this, item.widgetId, 0, REQ_RECONFIG_WIDGET, null)
                    } catch (e: Exception) { toast("再設定できません") }
                }
                entries += "削除" to { deleteItem(item) }
                menu(info?.loadLabel(packageManager) ?: "ウィジェット", entries)
            }
            ItemType.GROUP -> showGroupMenu(item)
        }
    }

    private fun showGroupMenu(item: HomeItem) {
        val g = item.group ?: return
        val entries = mutableListOf<Pair<String, () -> Unit>>()
        if (openFolderItem !== item) entries += "開く" to { openFolder(item) }
        entries += "名前を変更" to {
            showTextInput(this, "グループ名", g.name) { n ->
                if (n.isNotEmpty()) { g.name = n; save(); renderAll(); refreshFolder() }
            }
        }
        entries += "アイコンを作成・編集" to { openIconEditor(item) }
        entries += "アプリを追加・削除" to { editGroupApps(item) }
        if (g.mode == GroupMode.FOLDER) {
            entries += "ホームでスクロール表示（パネル）" to { setGroupMode(item, GroupMode.PANEL) }
        } else {
            entries += "アイコン表示（フォルダ）" to { setGroupMode(item, GroupMode.FOLDER) }
            entries += (if (g.scrollHorizontal) "縦スクロールにする" else "横スクロールにする") to {
                g.scrollHorizontal = !g.scrollHorizontal
                save(); renderAll()
            }
            entries += "サイズ変更" to {
                if (folder.isOpen) folder.close()
                startResize(item)
            }
        }
        entries += "グループを解除（アプリをホームに戻す）" to { ungroup(item) }
        entries += "削除" to {
            AlertDialog.Builder(this)
                .setMessage("「${g.name}」を削除しますか？（アプリ自体は削除されません）")
                .setPositiveButton("削除") { _, _ -> deleteItem(item) }
                .setNegativeButton("キャンセル", null)
                .showStyled()
            Unit
        }
        menu(g.name, entries)
    }

    private fun convertAppToGroup(item: HomeItem) {
        val page = ws.pageOf(item) ?: return
        val key = item.app ?: return
        val gi = HomeItem(ws.newId(), ItemType.GROUP, item.x, item.y, 1, 1, group = Group("グループ", mutableListOf(key)))
        page.items[page.items.indexOf(item)] = gi
        save(); renderAll()
        openFolder(gi)
    }

    private fun setGroupMode(item: HomeItem, mode: GroupMode, quiet: Boolean = false) {
        val g = item.group ?: return
        val page = ws.pageOf(item) ?: return
        val cols = ws.settings.cols
        val rows = ws.settings.rows
        g.mode = mode
        if (mode == GroupMode.FOLDER) {
            item.w = 1; item.h = 1
        } else {
            // 置ける最大のサイズを探す（最大 4x2 → 1x1）
            val sizes = listOf(min(cols, 4) to 2, min(cols, 3) to 2, min(cols, 4) to 1, 2 to 2, 2 to 1, 1 to 1)
            var done = false
            for ((w, h) in sizes) {
                if (page.isFree(item.x, item.y, w, h, cols, rows, item)) {
                    item.w = w; item.h = h; done = true; break
                }
                val x = (cols - w).coerceAtMost(item.x).coerceAtLeast(0)
                if (page.isFree(x, item.y, w, h, cols, rows, item)) {
                    item.x = x; item.w = w; item.h = h; done = true; break
                }
            }
            if (!done) { item.w = 1; item.h = 1 }
            if (!quiet && item.w * item.h == 1) toast("空きが少ないため 1×1 で表示しています。サイズ変更で広げられます")
        }
        if (quiet) return
        save(); renderAll()
        if (folder.isOpen) folder.close()
    }

    private fun editGroupApps(item: HomeItem) {
        val g = item.group ?: return
        showAppPicker(this, "「${g.name}」のアプリ", apps.apps, g.apps) { keys ->
            val keep = g.apps.filter { it in keys }
            val added = keys.filter { it !in g.apps }
            g.apps.clear()
            g.apps.addAll(keep + added)
            save(); renderAll(); refreshFolder()
        }
    }

    private fun ungroup(item: HomeItem) {
        val g = item.group ?: return
        val page = ws.pageOf(item) ?: return
        val pageIndex = ws.pages.indexOf(page)
        val x = item.x
        val y = item.y
        page.items.remove(item)
        if (openFolderItem === item) folder.close()
        g.apps.forEachIndexed { i, key ->
            val a = HomeItem(ws.newId(), ItemType.APP, app = key)
            placeItem(a, pageIndex, if (i == 0) Point(x, y) else null)
        }
        save(); renderAll()
    }

    private fun showGroupAppMenu(item: HomeItem, app: AppInfo, view: View) {
        val g = item.group ?: return
        menu(app.label, listOf(
            "グループから外す" to {
                g.apps.remove(app.key)
                save(); renderAll(); refreshFolder()
            },
            "ホームへ移動" to {
                g.apps.remove(app.key)
                val a = HomeItem(ws.newId(), ItemType.APP, app = app.key)
                placeItem(a, workspace.currentPage, null)
                save(); renderAll(); refreshFolder()
            },
            "アプリ情報" to { apps.openAppInfo(app.key) },
            "アンインストール" to { apps.uninstall(app.key) },
        ))
    }

    private fun showDrawerAppMenu(app: AppInfo) {
        menu(app.label, listOf(
            "ホームに追加" to {
                val a = HomeItem(ws.newId(), ItemType.APP, app = app.key)
                placeItem(a, workspace.currentPage, null)
                save(); renderAll()
                toast("ホームに追加しました")
            },
            "グループに追加" to { addToGroupChooser(app.key) },
            "アプリ情報" to { apps.openAppInfo(app.key) },
            "アンインストール" to { apps.uninstall(app.key) },
        ))
    }

    private fun addToGroupChooser(key: String) {
        val groups = ws.allItems().filter { it.type == ItemType.GROUP && it.group != null }.toList()
        val entries = mutableListOf<Pair<String, () -> Unit>>()
        for (gi in groups) {
            val g = gi.group!!
            entries += g.name to {
                if (key !in g.apps) g.apps += key
                save(); renderAll()
                toast("「${g.name}」に追加しました")
            }
        }
        entries += "＋ 新しいグループ" to {
            val g = Group("グループ", mutableListOf(key))
            val gi = HomeItem(ws.newId(), ItemType.GROUP, group = g)
            placeItem(gi, workspace.currentPage, null)
            save(); renderAll()
            drawer.close()
            openFolder(gi)
        }
        menu("グループに追加", entries)
    }

    // ---------------------------------------------------------------- folder

    private fun openFolder(item: HomeItem) {
        val g = item.group ?: return
        openFolderItem = item
        folder.bind(g.name, groupApps(g), 4)
        if (!folder.isOpen) folder.open()
    }

    private fun refreshFolder() {
        val item = openFolderItem ?: return
        val g = item.group ?: return
        if (ws.pageOf(item) == null) { folder.close(); return }
        folder.bind(g.name, groupApps(g), 4)
    }

    override fun onFolderAppClick(app: AppInfo, view: IconView) = apps.launch(app.key, view)

    override fun onFolderAppMenu(app: AppInfo, view: View) {
        val item = openFolderItem ?: return
        showGroupAppMenu(item, app, view)
    }

    override fun onFolderReorder(from: Int, to: Int) {
        val g = openFolderItem?.group ?: return
        val visible = groupApps(g).map { it.key }.toMutableList()
        if (from !in visible.indices || to !in visible.indices) return
        val k = visible.removeAt(from)
        visible.add(to, k)
        val hidden = g.apps.filter { it !in visible }
        g.apps.clear()
        g.apps.addAll(visible + hidden)
        save(); renderAll()
    }

    override fun onFolderRename(name: String) {
        val g = openFolderItem?.group ?: return
        if (g.name == name) return
        g.name = name
        save(); renderAll()
    }

    override fun onFolderAddApps() {
        openFolderItem?.let { editGroupApps(it) }
    }

    override fun onFolderEditIcon() {
        openFolderItem?.let { openIconEditor(it) }
    }

    override fun onFolderMore(anchor: View) {
        openFolderItem?.let { showGroupMenu(it) }
    }

    override fun onFolderClosed() {
        openFolderItem = null
    }

    // ---------------------------------------------------------------- group icon

    private fun openIconEditor(item: HomeItem) {
        val g = item.group ?: return
        iconEditor = GroupIconEditor(
            this, g.icon,
            render = { ic -> IconFactory.render(ic, g, groupIcons(g), 288) },
            onPickImage = {
                try {
                    @Suppress("DEPRECATION")
                    startActivityForResult(
                        Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"),
                        REQ_PICK_IMAGE,
                    )
                } catch (e: Exception) { toast("画像を選択できません") }
            },
            onSave = { ic ->
                g.icon = ic
                IconFactory.invalidateImage(ic.imagePath)
                iconEditor = null
                save(); renderAll(); refreshFolder()
            },
        ).also { it.show() }
    }

    /** 選んだ画像を正方形に切り抜いてアプリ内に保存する */
    private fun importImage(uri: Uri): String? = try {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        var sample = 1
        while (opts.outWidth / (sample * 2) >= 384 && opts.outHeight / (sample * 2) >= 384) sample *= 2
        val src = contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IllegalStateException("decode failed")
        val side = min(src.width, src.height)
        val cropped = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        val scaled = Bitmap.createScaledBitmap(cropped, 288, 288, true)
        val dir = File(filesDir, "icons").apply { mkdirs() }
        val f = File(dir, "icon_${System.currentTimeMillis()}.png")
        FileOutputStream(f).use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
        f.absolutePath
    } catch (e: Exception) {
        null
    }

    // ---------------------------------------------------------------- resize

    private fun startResize(item: HomeItem) {
        val page = ws.pageOf(item) ?: return
        val pageIndex = ws.pages.indexOf(page)
        if (pageIndex != workspace.currentPage) workspace.setPageImmediate(pageIndex)
        val cl = workspace.pageAt(pageIndex) ?: return
        val view = (0 until cl.childCount).map { cl.getChildAt(it) }.firstOrNull { it.tag === item } ?: return
        val loc = dragLayer.locationOf(cl)
        val cols = ws.settings.cols
        val rows = ws.settings.rows
        cl.showGrid = true
        val frame = ResizeFrame(
            this,
            cellToRect = { x, y, w, h -> cl.cellRect(x, y, w, h).apply { offset(loc[0], loc[1]) } },
            cellW = cl.cellWidth, cellH = cl.cellHeight, cols = cols, rows = rows,
            startX = item.x, startY = item.y, startW = item.w, startH = item.h,
            canApply = { x, y, w, h -> page.isFree(x, y, w, h, cols, rows, item) },
            onChange = { x, y, w, h ->
                item.x = x; item.y = y; item.w = w; item.h = h
                (view.layoutParams as? CellLayout.LP)?.let { lp -> lp.x = x; lp.y = y; lp.w = w; lp.h = h }
                view.requestLayout()
            },
            onDone = { finishResize() },
        )
        resizeFrame = frame
        dragLayer.addView(frame, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun finishResize() {
        val f = resizeFrame ?: return
        resizeFrame = null
        dragLayer.post { dragLayer.removeView(f) }
        for (i in 0 until workspace.pageCount) workspace.pageAt(i)?.showGrid = false
        save()
        renderAll()
    }

    // ---------------------------------------------------------------- drag & drop

    private inner class DragState(
        val item: HomeItem,
        val view: View,
        val fromHome: Boolean,
        val app: AppInfo?,
        val startX: Float,
        val startY: Float,
        val offX: Float,
        val offY: Float,
    ) {
        var moved = false
        var fromPage = -1
        var floating: View? = null
        var targetPage = -1
        var tx = 0
        var ty = 0
        var valid = false
        var merge: HomeItem? = null
        var edgeDir = 0
        var createdPages = 0
    }

    private var drag: DragState? = null

    override val isDragActive: Boolean get() = drag != null

    private fun startHomeDrag(item: HomeItem, view: View) {
        if (drag != null || resizeFrame != null || !view.isAttachedToWindow) return
        val loc = dragLayer.locationOf(view)
        drag = DragState(
            item, view, true, null,
            dragLayer.lastX, dragLayer.lastY,
            dragLayer.lastX - loc[0], dragLayer.lastY - loc[1],
        )
        view.animate().scaleX(1.06f).scaleY(1.06f).setDuration(120).start()
    }

    private fun startDrawerDrag(app: AppInfo, view: View) {
        if (drag != null) return
        val item = HomeItem(ws.newId(), ItemType.APP, w = 1, h = 1, app = app.key)
        drag = DragState(item, view, false, app, dragLayer.lastX, dragLayer.lastY, 0f, 0f)
    }

    override fun onDragTouch(ev: MotionEvent) {
        val d = drag ?: return
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (!d.moved) {
                    if (hypot(ev.x - d.startX, ev.y - d.startY) < touchSlop) return
                    beginMove(d)
                }
                moveFloating(d, ev.x, ev.y)
                updateDropTarget(d)
                checkEdge(d, ev.x)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!d.moved) {
                    drag = null
                    d.view.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                    if (ev.actionMasked == MotionEvent.ACTION_UP) {
                        if (d.fromHome) showItemMenu(d.item, d.view) else d.app?.let { showDrawerAppMenu(it) }
                    }
                } else {
                    drop(d)
                }
            }
        }
    }

    private fun beginMove(d: DragState) {
        d.moved = true
        val floating: View
        if (d.fromHome) {
            val page = ws.pageOf(d.item)
            d.fromPage = if (page != null) ws.pages.indexOf(page) else workspace.currentPage
            page?.items?.remove(d.item)
            val w = d.view.width
            val h = d.view.height
            (d.view.parent as? ViewGroup)?.removeView(d.view)
            floating = d.view
            dragLayer.addView(floating, FrameLayout.LayoutParams(w, h))
        } else {
            drawer.close(false)
            val cl = workspace.currentCellLayout
            val w = cl?.cellWidth?.toInt() ?: 80.dp
            val h = cl?.cellHeight?.toInt() ?: 90.dp
            floating = IconView(this).apply {
                icon = d.app?.icon
                label = d.app?.label ?: ""
                showLabel = ws.settings.showLabels
            }
            dragLayer.addView(floating, FrameLayout.LayoutParams(w, h))
            // 指の位置をアイコン中央に
            val dd = DragState(d.item, d.view, false, d.app, d.startX, d.startY, w / 2f, h / 2f)
            dd.moved = true
            drag = dd
            dd.floating = floating
            floating.alpha = 0.9f
            floating.scaleX = 1.1f; floating.scaleY = 1.1f
            for (i in 0 until workspace.pageCount) workspace.pageAt(i)?.showGrid = true
            return
        }
        d.floating = floating
        floating.alpha = 0.9f
        floating.scaleX = 1.06f; floating.scaleY = 1.06f
        for (i in 0 until workspace.pageCount) workspace.pageAt(i)?.showGrid = true
    }

    private fun moveFloating(d0: DragState, x: Float, y: Float) {
        val d = drag ?: d0
        val f = d.floating ?: return
        f.translationX = x - d.offX
        f.translationY = y - d.offY
    }

    private fun updateDropTarget(d0: DragState) {
        val d = drag ?: d0
        val f = d.floating ?: return
        val idx = workspace.currentPage
        val cl = workspace.pageAt(idx) ?: return
        val page = ws.pages.getOrNull(idx) ?: return
        val cols = ws.settings.cols
        val rows = ws.settings.rows
        for (i in 0 until workspace.pageCount) if (i != idx) workspace.pageAt(i)?.clearHint()
        val loc = dragLayer.locationOf(cl)
        // ページ切替アニメ中は scrollX 分だけずれるので補正
        val scrollOffset = workspace.scrollX - idx * workspace.width
        val left = f.translationX - loc[0] - scrollOffset
        val top = f.translationY - loc[1]
        d.targetPage = idx
        d.merge = null
        if (d.item.type == ItemType.APP) {
            val center = cl.cellAt(left + f.width / 2f, top + f.height / 2f)
            val occ = page.itemAt(center.x, center.y, d.item)
            if (occ != null && (occ.type == ItemType.GROUP || occ.type == ItemType.APP)) {
                d.merge = occ
                d.valid = true
                cl.setHint(occ.x, occ.y, occ.w, occ.h, valid = true, merge = true)
                return
            }
        }
        val p = cl.cellForTopLeft(left, top, d.item.w, d.item.h)
        d.tx = p.x
        d.ty = p.y
        d.valid = page.isFree(p.x, p.y, d.item.w, d.item.h, cols, rows, d.item)
        cl.setHint(p.x, p.y, d.item.w, d.item.h, d.valid)
    }

    private val flipRunnable = object : Runnable {
        override fun run() {
            val d = drag ?: return
            if (d.edgeDir == 0) return
            val target = workspace.currentPage + d.edgeDir
            if (target >= ws.pages.size) {
                // 最後のページの右端 → 新しいページを作る
                if (ws.pages.last().items.isEmpty()) return
                ws.pages += ws.newPage()
                workspace.addView(newCellLayout())
                indicator.count = ws.pages.size
                d.createdPages++
            }
            if (target < 0) return
            workspace.snapToPage(target)
            main.postDelayed({ drag?.let { updateDropTarget(it) } }, 420)
            main.postDelayed(this, 1000)
        }
    }

    private fun checkEdge(d0: DragState, x: Float) {
        val d = drag ?: d0
        val edge = 24.dp
        val dir = when {
            x < edge -> -1
            x > dragLayer.width - edge -> 1
            else -> 0
        }
        if (dir != d.edgeDir) {
            d.edgeDir = dir
            main.removeCallbacks(flipRunnable)
            if (dir != 0) main.postDelayed(flipRunnable, 500)
        }
    }

    private fun drop(d0: DragState) {
        val d = drag ?: d0
        drag = null
        main.removeCallbacks(flipRunnable)
        for (i in 0 until workspace.pageCount) workspace.pageAt(i)?.apply { clearHint(); showGrid = false }
        val f = d.floating
        if (f != null) {
            dragLayer.removeView(f)
            f.translationX = 0f; f.translationY = 0f
            f.scaleX = 1f; f.scaleY = 1f; f.alpha = 1f
        }
        val item = d.item
        var placed = false
        var openAfter: HomeItem? = null
        val merge = d.merge
        if (merge != null && item.type == ItemType.APP && item.app != null) {
            val key = item.app!!
            if (merge.type == ItemType.GROUP) {
                val g = merge.group!!
                if (key !in g.apps) g.apps += key
                placed = true
                toast("「${g.name}」に追加しました")
            } else if (merge.type == ItemType.APP && merge.app != null) {
                val page = ws.pages[d.targetPage]
                val keys = linkedSetOf(merge.app!!, key).toMutableList()
                val gi = HomeItem(ws.newId(), ItemType.GROUP, merge.x, merge.y, 1, 1, group = Group("グループ", keys))
                val i = page.items.indexOf(merge)
                if (i >= 0) {
                    page.items[i] = gi
                    placed = true
                    openAfter = gi
                }
            }
        } else if (d.valid && d.targetPage in ws.pages.indices) {
            item.x = d.tx
            item.y = d.ty
            ws.pages[d.targetPage].items += item
            placed = true
        }
        if (!placed) {
            if (d.fromHome) {
                ws.pages[d.fromPage.coerceIn(0, ws.pages.size - 1)].items += item
            }
            toast("そこには置けません")
        }
        // ドラッグ中に作られた空ページを片付ける
        while (d.createdPages > 0 && ws.pages.size > 1 && ws.pages.last().items.isEmpty()) {
            ws.pages.removeAt(ws.pages.size - 1)
            d.createdPages--
        }
        save()
        renderAll()
        openAfter?.let { openFolder(it) }
    }

    // ---------------------------------------------------------------- look & settings

    private val blurSupported: Boolean
        get() = Build.VERSION.SDK_INT >= 31 && windowManager.isCrossWindowBlurEnabled

    /** 壁紙のぼかし・暗さと、ドロワー/グループを開いた時の背景演出 */
    private fun updateBackdrop() {
        val t = Look.theme
        var blur = t.wallBlur.toFloat()
        blur += (t.drawerBlur - blur) * drawerP
        blur += (maxOf(t.folderBlur.toFloat(), blur) - blur) * folderP
        setWindowBlur(Look.blurPx(blur.toInt()))
        val extraDim = if (blurSupported) 0f else blur * 0.3f
        val dim = (t.wallDim + extraDim).coerceIn(0f, 95f)
        dragLayer.setBackgroundColor(Color.argb((dim / 100f * 255).toInt(), 0, 0, 0))
        val cover = maxOf(drawerP, folderP * 0.6f)
        content.alpha = 1f - cover
        val scale = 1f - 0.05f * maxOf(drawerP, folderP)
        content.scaleX = scale
        content.scaleY = scale
    }

    private fun setWindowBlur(px: Int) {
        if (Build.VERSION.SDK_INT < 31 || !blurSupported) return
        if (px == currentBlur) return
        currentBlur = px
        val lp = window.attributes
        if (px > 0) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            lp.blurBehindRadius = px
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            lp.blurBehindRadius = 0
        }
        window.attributes = lp
    }

    private val applyLookRunnable = Runnable { applyLook() }

    private fun applyLook() {
        updateBackdrop()
        handle.setTextColor(Look.theme.labelColor)
        drawer.applyTheme()
        indicator.invalidate()
        renderAll()
        refreshFolder()
    }

    private fun openSettings() {
        if (settingsSheet != null) return
        closeOverlays()
        val sheet = SettingsSheet(this, ws.theme, ws.settings, this)
        settingsSheet = sheet
        sheet.open(dragLayer)
    }

    override fun onThemePreview(theme: Theme) {
        Look.theme = theme
        main.removeCallbacks(applyLookRunnable)
        main.postDelayed(applyLookRunnable, 24)
    }

    override fun onSettingsDone(theme: Theme, settings: Settings) {
        settingsSheet = null
        ws.theme = theme
        Look.theme = theme
        val s = ws.settings
        val gridChanged = s.cols != settings.cols || s.rows != settings.rows
        ws.settings = settings
        if (gridChanged) ws.normalize()
        drawer.setColumns(settings.drawerCols)
        save()
        applyLook()
    }

    override fun onSettingsCancel(original: Theme) {
        settingsSheet = null
        Look.theme = ws.theme
        applyLook()
    }

    override fun onBulkGroupIcons(shape: IconShape?, style: IconStyle?, bgColor: Int?, bgAlpha: Int?) {
        val groups = ws.allItems().mapNotNull { it.group }.toList()
        for (g in groups) {
            shape?.let { g.icon.shape = it }
            style?.let { g.icon.style = it }
            bgColor?.let { g.icon.bgColor = Look.withAlpha(it, bgAlpha ?: 100) }
        }
        save(); renderAll()
        toast("${groups.size}個のグループに適用しました")
    }

    override fun onBulkGroupMode(panel: Boolean) {
        val items = ws.allItems().filter { it.type == ItemType.GROUP && it.group != null }.toList()
        for (it in items) setGroupMode(it, if (panel) GroupMode.PANEL else GroupMode.FOLDER, quiet = true)
        save(); renderAll()
        toast("${items.size}個のグループを変更しました")
    }

    override fun onCopyTheme(theme: Theme) {
        val cm = getSystemService(ClipboardManager::class.java)
        cm?.setPrimaryClip(ClipData.newPlainText("NLauncher theme", theme.toJson().toString()))
        toast("テーマをコピーしました")
    }

    override fun onPasteTheme(): Theme? {
        val cm = getSystemService(ClipboardManager::class.java)
        val text = cm?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
        return try {
            Theme.fromJson(org.json.JSONObject(text ?: ""))
        } catch (e: Exception) {
            toast("テーマのデータが見つかりません")
            null
        }
    }

    // ---------------------------------------------------------------- misc

    private fun save() = store.save(ws)

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        private const val HOST_ID = 0x4E4C
        private const val REQ_PICK_WIDGET = 11
        private const val REQ_BIND_WIDGET = 12
        private const val REQ_CONFIG_WIDGET = 13
        private const val REQ_RECONFIG_WIDGET = 14
        private const val REQ_PICK_IMAGE = 15
    }
}
