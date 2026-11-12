package com.wallpaperfit.app.storage

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/**
 * Mira en GitHub si hay un APK más nuevo que el instalado. Solo se conecta cuando se abre la app
 * (como mucho una vez al día) o al tocar "Buscar actualizaciones": nada en segundo plano.
 *
 * Cada versión es una release con su etiqueta ("v1.2.0"): hay novedad si su número es mayor que
 * el de la versión instalada.
 */
object UpdateChecker {

    /** Una versión nueva: cuándo se publicó (ms) y de dónde se descarga. */
    data class Update(val publishedAt: Long, val downloadUrl: String)

    sealed interface Result {
        data class Available(val update: Update) : Result
        data object UpToDate : Result
        data object Failed : Result
    }

    /** Si la versión publicada ([published], "v1.2.0") es mayor que la instalada ([installed], "1.1.0"). */
    fun isNewer(published: String, installed: String): Boolean {
        val a = numbers(published)
        val b = numbers(installed)
        if (a.isEmpty() || b.isEmpty()) return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** "v1.2.0-beta" → [1, 2, 0]. */
    private fun numbers(version: String): List<Int> =
        version.trim().removePrefix("v").substringBefore('-').split('.').mapNotNull { it.toIntOrNull() }

    /** Comprueba ahora (al tocar "Buscar actualizaciones"). */
    suspend fun check(context: Context): Result = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val result = try {
            val latest = latestRelease() ?: return@withContext Result.Failed
            val installed = app.packageManager.getPackageInfo(app.packageName, 0).versionName.orEmpty()
            if (isNewer(latest.tag, installed)) Result.Available(latest.update) else Result.UpToDate
        } catch (e: Exception) {
            Result.Failed
        }
        // Sin conexión no cuenta como comprobado: se vuelve a intentar la próxima vez que se abra.
        if (result != Result.Failed) prefs(app).edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
        result
    }

    /** Al abrir la app: comprueba como mucho una vez al día; si no toca, null. */
    suspend fun checkDaily(context: Context): Update? {
        val last = prefs(context).getLong(KEY_LAST_CHECK, 0L)
        if (System.currentTimeMillis() - last < DAY_MS) return null
        return (check(context) as? Result.Available)?.update
    }

    private class Release(val tag: String, val update: Update)

    /** La última release de GitHub: su etiqueta y su APK. */
    private fun latestRelease(): Release? {
        val connection = URL(LATEST_RELEASE).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "WallpaperEditor")
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val assets = json.optJSONArray("assets") ?: return null
            // Solo releases publicadas por HTTPS desde GitHub: el enlace se abre en el navegador.
            val url = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk") }
                ?.optString("browser_download_url")
                ?.takeIf { it.startsWith(DOWNLOAD_PREFIX) } ?: return null
            Release(json.optString("tag_name"), Update(Instant.parse(json.optString("published_at")).toEpochMilli(), url))
        } finally {
            connection.disconnect()
        }
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    private const val LATEST_RELEASE = "https://api.github.com/repos/AnxoBT/wallpaper-editor/releases/latest"
    private const val DOWNLOAD_PREFIX = "https://github.com/AnxoBT/wallpaper-editor/releases/download/"
    private const val PREFERENCES = "updates"
    private const val KEY_LAST_CHECK = "last_check"
    private const val DAY_MS = 24 * 60 * 60 * 1000L
    private const val TIMEOUT_MS = 8000
}
