package android.renderscript

import android.content.Context
import android.graphics.Bitmap

/*
 * No hay RenderScript en un PC: al fallar create(), BlurEngine usa su desenfoque por software, el
 * mismo que en los móviles que no lo tienen.
 */
class RenderScript private constructor() {
    fun destroy() = Unit

    companion object {
        fun create(context: Context): RenderScript = throw UnsupportedOperationException("RenderScript no existe en Windows")
    }
}

class Element private constructor() {
    companion object {
        fun U8_4(rs: RenderScript): Element = Element()
    }
}

class Allocation private constructor() {
    fun copyTo(bitmap: Bitmap) = Unit

    fun destroy() = Unit

    companion object {
        fun createFromBitmap(rs: RenderScript, bitmap: Bitmap): Allocation = Allocation()
    }
}

class ScriptIntrinsicBlur private constructor() {
    fun setRadius(radius: Float) = Unit

    fun setInput(input: Allocation) = Unit

    fun forEach(output: Allocation) = Unit

    fun destroy() = Unit

    companion object {
        fun create(rs: RenderScript, element: Element): ScriptIntrinsicBlur = ScriptIntrinsicBlur()
    }
}
