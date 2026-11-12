package com.wallpaperfit.app.storage

import android.content.Context

/** Versión de Windows: no busca actualizaciones (el aviso es para el APK del móvil). */
object UpdateChecker {

    data class Update(val publishedAt: Long, val downloadUrl: String)

    sealed interface Result {
        data class Available(val update: Update) : Result
        data object UpToDate : Result
        data object Failed : Result
    }

    suspend fun check(context: Context): Result = Result.UpToDate

    suspend fun checkDaily(context: Context): Update? = null
}
