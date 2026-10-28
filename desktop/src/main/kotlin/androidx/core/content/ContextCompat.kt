package androidx.core.content

import android.content.Context

/** En un PC no hay permisos de fotos: todos están concedidos. */
object ContextCompat {
    fun checkSelfPermission(context: Context, permission: String): Int = 0
}
