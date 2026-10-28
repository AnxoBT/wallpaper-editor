package com.wallpaperfit.app.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.pow

/** Con ratón no se puede pellizcar: la rueda hace de zoom y Mayús + rueda, de giro. */
fun Modifier.mouseWheel(onWheel: (position: Offset, zoom: Float, rotation: Float) -> Unit): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            if (event.type != PointerEventType.Scroll) continue
            val change = event.changes.first()
            val delta = change.scrollDelta.y
            if (delta == 0f) continue
            if (event.keyboardModifiers.isShiftPressed) {
                onWheel(change.position, 1f, delta * WHEEL_ROTATION)
            } else {
                onWheel(change.position, WHEEL_ZOOM.pow(-delta), 0f)
            }
            change.consume()
        }
    }
}

private const val WHEEL_ZOOM = 1.1f
private const val WHEEL_ROTATION = 2f
