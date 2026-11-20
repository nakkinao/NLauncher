package com.nlauncher.data

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import android.view.View
import android.widget.Toast
import java.text.Collator
import java.util.concurrent.Executors

class AppInfo(
    val key: String,
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
    val icon: Drawable,
)

/** インストール済みアプリ一覧を管理する */
class AppsRepository(private val context: Context) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    var apps: List<AppInfo> = emptyList()
        private set
    private var byKey: Map<String, AppInfo> = emptyMap()
    var loaded = false
        private set

    private val listeners = mutableListOf<() -> Unit>()

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) = reload()
        override fun onPackageAdded(packageName: String?, user: UserHandle?) = reload()
        override fun onPackageChanged(packageName: String?, user: UserHandle?) = reload()
        override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = reload()
        override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = reload()
        override fun onPackagesSuspended(packageNames: Array<out String>?, user: UserHandle?) = reload()
        override fun onPackagesUnsuspended(packageNames: Array<out String>?, user: UserHandle?) = reload()
    }

    init {
        launcherApps.registerCallback(callback, main)
    }

    fun addListener(l: () -> Unit) {
        listeners += l
    }

    fun get(key: String?): AppInfo? = key?.let { byKey[it] }

    fun keyOf(component: ComponentName, user: UserHandle): String =
        "${component.flattenToShortString()}#${userManager.getSerialNumberForUser(user)}"

    fun reload() {
        executor.execute {
            val result = mutableListOf<AppInfo>()
            try {
                for (user in launcherApps.profiles) {
                    for (info in launcherApps.getActivityList(null, user)) {
                        if (info.componentName.packageName == context.packageName) continue
                        val icon = try {
                            info.getBadgedIcon(0)
                        } catch (e: Exception) {
                            context.packageManager.defaultActivityIcon
                        }
                        result += AppInfo(
                            keyOf(info.componentName, user),
                            info.label?.toString() ?: info.componentName.packageName,
                            info.componentName,
                            user,
                            icon,
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("AppsRepository", "load failed", e)
            }
            val collator = Collator.getInstance()
            result.sortWith { a, b -> collator.compare(a.label, b.label) }
            main.post {
                apps = result
                byKey = result.associateBy { it.key }
                loaded = true
                listeners.forEach { it() }
            }
        }
    }

    fun launch(key: String, view: View?) {
        val app = byKey[key]
        if (app == null) {
            Toast.makeText(context, "アプリが見つかりません", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            var bounds: Rect? = null
            var opts: android.os.Bundle? = null
            if (view != null && view.isAttachedToWindow) {
                val loc = IntArray(2)
                view.getLocationOnScreen(loc)
                bounds = Rect(loc[0], loc[1], loc[0] + view.width, loc[1] + view.height)
                opts = ActivityOptions.makeScaleUpAnimation(view, 0, 0, view.width, view.height).toBundle()
            }
            launcherApps.startMainActivity(app.component, app.user, bounds, opts)
        } catch (e: Exception) {
            Toast.makeText(context, "起動できませんでした", Toast.LENGTH_SHORT).show()
        }
    }

    fun openAppInfo(key: String) {
        val app = byKey[key] ?: return
        try {
            launcherApps.startAppDetailsActivity(app.component, app.user, null, null)
        } catch (e: Exception) {
            Log.w("AppsRepository", "app info failed", e)
        }
    }

    fun uninstall(key: String) {
        val app = byKey[key] ?: return
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.component.packageName}"))
            .putExtra(Intent.EXTRA_USER, app.user)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "アンインストールできません", Toast.LENGTH_SHORT).show()
        }
    }
}
