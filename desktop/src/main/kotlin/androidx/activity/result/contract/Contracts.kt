package androidx.activity.result.contract

import android.net.Uri
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import com.wallpaperfit.app.desktop.FileDialogs
import com.wallpaperfit.app.desktop.PhotoLibrary

/** Lo que en Android abre otra pantalla del sistema; en el PC, sus diálogos de archivos. */
abstract class ActivityResultContract<I, O> {
    abstract fun run(input: I): O
}

object ActivityResultContracts {
    class PickVisualMedia : ActivityResultContract<PickVisualMediaRequest, Uri?>() {
        // La carpeta de una foto abierta a mano entra en la biblioteca, igual que las demás.
        override fun run(input: PickVisualMediaRequest): Uri? = FileDialogs.openImage()?.let {
            PhotoLibrary.addFolder(it.parentFile)
            Uri.fromFile(it)
        }

        object ImageOnly
    }

    class OpenDocumentTree : ActivityResultContract<Uri?, Uri?>() {
        override fun run(input: Uri?): Uri? = FileDialogs.chooseFolder()?.let(Uri::fromFile)
    }

    class OpenDocument : ActivityResultContract<Array<String>, Uri?>() {
        override fun run(input: Array<String>): Uri? = FileDialogs.open("*.json")?.let(Uri::fromFile)
    }

    class CreateDocument(private val mimeType: String) : ActivityResultContract<String, Uri?>() {
        override fun run(input: String): Uri? = FileDialogs.save(input)?.let(Uri::fromFile)
    }

    class RequestPermission : ActivityResultContract<String, Boolean>() {
        override fun run(input: String) = true
    }

    class RequestMultiplePermissions : ActivityResultContract<Array<String>, Map<String, Boolean>>() {
        override fun run(input: Array<String>) = input.associateWith { true }
    }

    /** Solo pide permiso para sobrescribir una foto, y en Windows no hace falta. */
    class StartIntentSenderForResult : ActivityResultContract<IntentSenderRequest, ActivityResult>() {
        override fun run(input: IntentSenderRequest) = ActivityResult(android.app.Activity.RESULT_OK)
    }
}
