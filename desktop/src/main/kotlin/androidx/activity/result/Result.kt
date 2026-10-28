package androidx.activity.result

import android.content.IntentSender

abstract class ActivityResultLauncher<I> {
    abstract fun launch(input: I)
}

class PickVisualMediaRequest(val mediaType: Any? = null)

class IntentSenderRequest private constructor() {
    class Builder(sender: IntentSender) {
        fun build() = IntentSenderRequest()
    }
}

class ActivityResult(val resultCode: Int)
