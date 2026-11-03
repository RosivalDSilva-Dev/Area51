package com.vidro.launcher.data

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.vidro.launcher.glass.GlassStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Ajustes do vidro que o usuário calibra no painel (salvos entre reinícios). */
data class GlassTuning(
    val refraction: Float = 24f,   // dp
    val bevel: Float = 20f,        // dp
    val blur: Float = 3f,          // dp
    val dispersion: Float = 0.22f,
    val highlight: Float = 0.55f,
    val tint: Float = 0.06f,       // quanto de branco
    val saturation: Float = 1.35f,
) {
    fun toStyle(): GlassStyle = GlassStyle(
        bevel = bevel.dp,
        refraction = refraction.dp,
        blur = blur.dp,
        dispersion = dispersion,
        highlight = highlight,
        tint = Color.White.copy(alpha = tint),
        saturation = saturation,
    )
}

class LauncherState(private val context: Context) {
    private val prefs = context.getSharedPreferences("vidro", Context.MODE_PRIVATE)
    private val wallpaperFile = File(context.filesDir, "papel_de_parede.jpg")

    var dock by mutableStateOf(readList("dock"))
        private set
    var home by mutableStateOf(readList("home"))
        private set
    var tuning by mutableStateOf(readTuning())
        private set
    var wallpaper by mutableStateOf<ImageBitmap?>(null)
        private set

    private fun readList(key: String): List<String>? =
        prefs.getString(key, null)?.split("|")?.filter { it.isNotBlank() }

    private fun writeList(key: String, list: List<String>) {
        prefs.edit().putString(key, list.joinToString("|")).apply()
    }

    fun updateDock(list: List<String>) {
        dock = list
        writeList("dock", list)
    }

    fun updateHome(list: List<String>) {
        home = list
        writeList("home", list)
    }

    /** Monta dock e tela inicial na primeira vez que os apps carregam. */
    fun ensureDefaults(apps: List<AppEntry>, dockPackages: List<String>) {
        if (apps.isEmpty()) return
        val byPkg = apps.associateBy { it.pkg }
        if (dock == null) {
            updateDock(dockPackages.mapNotNull { byPkg[it]?.key }.take(4))
        }
        if (home == null) {
            val preferred = listOf(
                "com.google.android.youtube", "com.instagram.android", "com.google.android.gm",
                "com.google.android.apps.photos", "com.google.android.calendar", "com.google.android.apps.maps",
                "com.android.vending", "com.google.android.deskclock", "com.android.settings",
                "com.google.android.apps.messaging", "com.spotify.music", "com.google.android.calculator",
            )
            val dockKeys = dock.orEmpty().toSet()
            val start = preferred.mapNotNull { byPkg[it]?.key }.filter { it !in dockKeys }
            val rest = apps.map { it.key }.filter { it !in dockKeys && it !in start }
            updateHome((start + rest).take(16))
        }
    }

    fun updateTuning(t: GlassTuning) {
        tuning = t
        prefs.edit()
            .putFloat("refraction", t.refraction)
            .putFloat("bevel", t.bevel)
            .putFloat("blur", t.blur)
            .putFloat("dispersion", t.dispersion)
            .putFloat("highlight", t.highlight)
            .putFloat("tint", t.tint)
            .putFloat("saturation", t.saturation)
            .apply()
    }

    private fun readTuning(): GlassTuning {
        val d = GlassTuning()
        return GlassTuning(
            refraction = prefs.getFloat("refraction", d.refraction),
            bevel = prefs.getFloat("bevel", d.bevel),
            blur = prefs.getFloat("blur", d.blur),
            dispersion = prefs.getFloat("dispersion", d.dispersion),
            highlight = prefs.getFloat("highlight", d.highlight),
            tint = prefs.getFloat("tint", d.tint),
            saturation = prefs.getFloat("saturation", d.saturation),
        )
    }

    suspend fun loadWallpaper() {
        wallpaper = withContext(Dispatchers.IO) {
            if (wallpaperFile.exists()) decodeScaled { BitmapFactory.decodeFile(wallpaperFile.path, it) }?.asImageBitmap() else null
        }
    }

    suspend fun setWallpaperFromUri(uri: Uri) {
        withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { input ->
                wallpaperFile.outputStream().use { input.copyTo(it) }
            }
        }
        loadWallpaper()
    }

    /** Copia o papel de parede atual do sistema. Lança SecurityException se faltar permissão. */
    suspend fun copySystemWallpaper() {
        withContext(Dispatchers.IO) {
            val drawable = WallpaperManager.getInstance(context).drawable ?: return@withContext
            val metrics = context.resources.displayMetrics
            val bmp = drawable.toBitmap(
                drawable.intrinsicWidth.takeIf { it > 0 } ?: metrics.widthPixels,
                drawable.intrinsicHeight.takeIf { it > 0 } ?: metrics.heightPixels,
            )
            wallpaperFile.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        }
        loadWallpaper()
    }

    fun resetWallpaper() {
        wallpaperFile.delete()
        wallpaper = null
    }

    private fun decodeScaled(decode: (BitmapFactory.Options) -> Bitmap?): Bitmap? {
        val target = context.resources.displayMetrics.heightPixels
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        decode(bounds)
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= target / 2 &&
            bounds.outHeight / (sample * 2) >= target
        ) {
            sample *= 2
        }
        return decode(BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
