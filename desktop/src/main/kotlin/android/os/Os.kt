package android.os

object Build {
    object VERSION {
        /** Android 8, la más antigua que admite la app: así no entra por las rutas de MediaStore moderno. */
        const val SDK_INT = 26
    }

    object VERSION_CODES {
        const val P = 28
        const val Q = 29
        const val R = 30
        const val TIRAMISU = 33
        const val UPSIDE_DOWN_CAKE = 34
    }
}

class Looper private constructor() {
    companion object {
        fun getMainLooper() = Looper()
    }
}

/** Solo lo usa la vigilancia de la galería de Android, que en el PC no hace nada. */
class Handler(looper: Looper) {
    fun removeCallbacks(runnable: Runnable) = Unit

    fun postDelayed(runnable: Runnable, delay: Long) = true
}
