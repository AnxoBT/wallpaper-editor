package com.wallpaperfit.app.storage

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/**
 * Mira en GitHub si hay un APK más nuevo que el instalado. Solo se conecta cuando se abre la app
 * (como mucho una vez al día) o al tocar "Buscar actualizaciones": nada en segundo plano.
 *
 * La versión se queda siempre en 1.0 y el APK de la release se va sustituyendo, así que no se
 * compara el número de versión: hay novedad si el APK publicado es distinto del instalado (otro
 * tamaño) y se subió después de instalar este.
 */
object UpdateChecker {

    /** Una versión nueva: cuándo se publicó (ms) y de dónde se descarga. */
    data class Update(val publishedAt: Long, val downloadUrl: String)

    sealed interface Result {
        data class Available(val update: Update) : Result
        data object UpToDate : Result
        data object Failed : Result
    }

    /** Si el APK publicado ([assetSize], [assetTime]) es una versión nueva respecto a la instalada. */
    fun isNewer(assetSize: Long, assetTime: Long, installedSize: Long, installedAt: Long): Boolean =
        assetSize > 0 && assetSize != installedSize && assetTime > installedAt

    /** Comprueba ahora (al tocar "Buscar actualizaciones"). */
    suspend fun check(context: Context): Result = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val result = try {
            val asset = latestApk() ?: return@withContext Result.Failed
            val installedSize = File(app.applicationInfo.sourceDir).length()
            val installedAt = app.packageManager.getPackageInfo(app.packageName, 0).lastUpdateTime
            if (isNewer(asset.size, asset.update.publishedAt, installedSize, installedAt)) Result.Available(asset.update) else Result.UpToDate
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

    private class Asset(val size: Long, val update: Update)

    /** El APK de la última release de GitHub. */
    private fun latestApk(): Asset? {
        val connection = URL(LATEST_RELEASE).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "WallpaperEditor")
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val assets = json.optJSONArray("assets") ?: return null
            (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk") }
                ?.let { apk ->
                    Asset(
                        size = apk.optLong("size"),
                        update = Update(Instant.parse(apk.optString("updated_at")).toEpochMilli(), apk.optString("browser_download_url")),
                    )
                }
        } finally {
            connection.disconnect()
        }
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    private const val LATEST_RELEASE = "https://api.github.com/repos/AnxoBT/wallpaper-editor/releases/latest"
    private const val PREFERENCES = "updates"
    private const val KEY_LAST_CHECK = "last_check"
    private const val DAY_MS = 24 * 60 * 60 * 1000L
    private const val TIMEOUT_MS = 8000
}
