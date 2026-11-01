package com.vidro.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.MediaStore
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.text.Normalizer
import java.util.Locale

data class AppEntry(
    val key: String,
    val label: String,
    val pkg: String,
    val component: ComponentName,
    val user: UserHandle,
    val icon: ImageBitmap,
) {
    val searchText: String = normalize(label)
}

fun normalize(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)

class AppRepository(private val context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var loadJob: Job? = null

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) = reload()
        override fun onPackageAdded(packageName: String?, user: UserHandle?) = reload()
        override fun onPackageChanged(packageName: String?, user: UserHandle?) = reload()
        override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = reload()
        override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = reload()
    }

    fun start() {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        reload()
    }

    fun stop() {
        launcherApps.unregisterCallback(callback)
    }

    fun reload() {
        loadJob?.cancel()
        loadJob = scope.launch {
            _apps.value = withContext(Dispatchers.IO) { loadAll() }
        }
    }

    private fun loadAll(): List<AppEntry> {
        val iconPx = (context.resources.displayMetrics.density * 64).toInt()
        val collator = Collator.getInstance(Locale.forLanguageTag("pt-BR"))
        val result = ArrayList<AppEntry>()
        for (user in launcherApps.profiles) {
            for (info in launcherApps.getActivityList(null, user)) {
                val cn = info.componentName
                if (cn.packageName == context.packageName) continue
                val icon = try {
                    info.getBadgedIcon(0).toBitmap(iconPx, iconPx).asImageBitmap()
                } catch (t: Throwable) {
                    continue
                }
                result += AppEntry(
                    key = "${cn.flattenToString()}#${user.hashCode()}",
                    label = info.label?.toString() ?: cn.packageName,
                    pkg = cn.packageName,
                    component = cn,
                    user = user,
                    icon = icon,
                )
            }
        }
        result.sortWith { a, b -> collator.compare(a.label, b.label) }
        return result
    }

    fun launch(app: AppEntry, bounds: android.graphics.Rect?) {
        try {
            launcherApps.startMainActivity(app.component, app.user, bounds, null)
        } catch (_: Throwable) {
        }
    }

    fun showAppInfo(app: AppEntry) {
        try {
            launcherApps.startAppDetailsActivity(app.component, app.user, null, null)
        } catch (_: Throwable) {
        }
    }

    fun uninstall(app: AppEntry) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.pkg}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: Throwable) {
        }
    }

    /** Pacotes padrão do sistema para montar o dock inicial. */
    fun defaultDockPackages(installed: Set<String>): List<String> {
        val pm = context.packageManager
        fun resolve(intent: Intent): String? {
            val pkg = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            return if (pkg == null || pkg == "android" || pkg !in installed) null else pkg
        }

        val dial = resolve(Intent(Intent.ACTION_DIAL))
            ?: listOf("com.google.android.dialer", "com.android.dialer").firstOrNull { it in installed }
        val chat = listOf("com.whatsapp", "com.whatsapp.w4b").firstOrNull { it in installed }
            ?: resolve(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")))
        val browser = resolve(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")))
            ?: "com.android.chrome".takeIf { it in installed }
        val camera = resolve(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
            ?: listOf("com.motorola.camera3", "com.motorola.camera5", "com.motorola.camera2")
                .firstOrNull { it in installed }
        return listOfNotNull(dial, chat, browser, camera).distinct()
    }
}
