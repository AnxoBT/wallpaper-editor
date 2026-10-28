package android.app

import android.content.Context
import android.content.IntentSender

open class Application : Context()

/** La ventana del PC hace de actividad (AppLanguage.set la recibe de Ajustes). */
open class Activity : Application() {
    companion object {
        const val RESULT_OK = -1
    }
}

/** En Windows nunca se lanza: se puede escribir en las fotos sin pedir permiso. */
class RecoverableSecurityException private constructor() : SecurityException() {
    val userAction: RemoteAction get() = throw UnsupportedOperationException()

    class RemoteAction {
        val actionIntent: PendingIntent get() = throw UnsupportedOperationException()
    }

    class PendingIntent {
        val intentSender: IntentSender get() = throw UnsupportedOperationException()
    }
}
