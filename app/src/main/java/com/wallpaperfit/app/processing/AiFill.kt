package com.wallpaperfit.app.processing

import android.graphics.Bitmap
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Usos del modelo de [AiInpainter] (que solo entiende ventanas de 512 × 512) sobre imágenes de
 * cualquier tamaño:
 * borrar objetos, manchas o textos. Cada grupo de zonas cercanas se rellena en una ventana con
 * contexto alrededor.
 */
internal class AiFill(private val model: AiInpainter) {

    private val size = AiInpainter.SIZE

    // region Borrar

    /** Rellena [hole] con IA. Devuelve una copia de [pixels]; los píxeles conocidos no cambian. */
    fun fillHoles(pixels: IntArray, width: Int, height: Int, hole: BooleanArray, checkCancelled: () -> Unit): IntArray {
        val out = pixels.copyOf()
        val remaining = hole.copyOf()
        for (box in holeGroups(hole, width, height)) {
            checkCancelled()
            // Ventana cuadrada: la zona a borrar ocupa como mucho ~40 % del lado, el resto es contexto.
            val side = max(box.width, box.height)
            val window = min(max(width, height), max(MIN_WINDOW, (side * CONTEXT_FACTOR).roundToInt()))
            if (window > size * MAX_DOWNSCALE) {
                fillTiled(out, width, height, remaining, box, checkCancelled)
                continue
            }
            val cx = (box.left + box.right) / 2
            val cy = (box.top + box.bottom) / 2
            val ww = min(window, width)
            val wh = min(window, height)
            val left = (cx - ww / 2).coerceIn(0, width - ww)
            val top = (cy - wh / 2).coerceIn(0, height - wh)
            runWindow(out, width, remaining, left, top, ww, wh, Region(left, top, left + ww, top + wh))
        }
        return out
    }

    /**
     * Zonas grandes (una línea de texto larga, un título y su subtítulo…): en vez de una sola
     * ventana enorme reducida a 512 px (que emborrona y deforma el fondo al volver a ampliarla),
     * se rellena por trozos a resolución real. Cada ventana de 512 px solo escribe su celda central
     * y ve lo ya rellenado por las anteriores, así que el resultado es continuo y nítido.
     */
    private fun fillTiled(out: IntArray, width: Int, height: Int, remaining: BooleanArray, box: Box, checkCancelled: () -> Unit) {
        val ww = min(size, width)
        val wh = min(size, height)
        val cell = TILE_CELL
        var y = box.top
        while (y < box.bottom) {
            var x = box.left
            while (x < box.right) {
                checkCancelled()
                val cellRight = min(box.right, x + cell)
                val cellBottom = min(box.bottom, y + cell)
                val left = ((x + cellRight) / 2 - ww / 2).coerceIn(0, width - ww)
                val top = ((y + cellBottom) / 2 - wh / 2).coerceIn(0, height - wh)
                runWindow(out, width, remaining, left, top, ww, wh, Region(x, y, cellRight, cellBottom))
                x = cellRight
            }
            y = min(box.bottom, y + cell)
        }
    }

    private class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width get() = right - left
        val height get() = bottom - top
    }

    /**
     * Zonas a rellenar agrupadas: componentes conexas cuyos rectángulos (con margen) se solapan se
     * procesan juntas, así un texto de varias letras va en una sola ventana.
     */
    private fun holeGroups(hole: BooleanArray, width: Int, height: Int): List<Box> {
        val visited = BooleanArray(hole.size)
        val stack = IntArray(hole.size)
        val boxes = ArrayList<Box>()
        for (start in hole.indices) {
            if (!hole[start] || visited[start]) continue
            var top = 0
            stack[top++] = start
            visited[start] = true
            var l = width
            var t = height
            var r = -1
            var b = -1
            while (top > 0) {
                val p = stack[--top]
                val x = p % width
                val y = p / width
                if (x < l) l = x
                if (x > r) r = x
                if (y < t) t = y
                if (y > b) b = y
                if (x > 0 && hole[p - 1] && !visited[p - 1]) { visited[p - 1] = true; stack[top++] = p - 1 }
                if (x < width - 1 && hole[p + 1] && !visited[p + 1]) { visited[p + 1] = true; stack[top++] = p + 1 }
                if (y > 0 && hole[p - width] && !visited[p - width]) { visited[p - width] = true; stack[top++] = p - width }
                if (y < height - 1 && hole[p + width] && !visited[p + width]) { visited[p + width] = true; stack[top++] = p + width }
            }
            boxes += Box(l, t, r + 1, b + 1)
        }
        // Fusiona grupos cercanos hasta que no quede ninguno que solapar.
        var merged = boxes.toMutableList()
        var changed = true
        while (changed) {
            changed = false
            val next = ArrayList<Box>()
            for (box in merged) {
                val margin = max(8, max(box.width, box.height) / 2)
                val index = next.indexOfFirst { other ->
                    box.left - margin < other.right && other.left < box.right + margin &&
                        box.top - margin < other.bottom && other.top < box.bottom + margin
                }
                if (index >= 0) {
                    val o = next[index]
                    next[index] = Box(min(o.left, box.left), min(o.top, box.top), max(o.right, box.right), max(o.bottom, box.bottom))
                    changed = true
                } else {
                    next += box
                }
            }
            merged = next
        }
        return merged
    }

    // endregion

    /** Rectángulo [left, right) × [top, bottom) en píxeles. */
    class Region(val left: Int, val top: Int, val right: Int, val bottom: Int)

    /**
     * Ejecuta el modelo en la ventana ([left], [top], [ww] × [wh]) y copia el resultado solo en los
     * píxeles pendientes que caen dentro de [write], que dejan de estar pendientes.
     */
    private fun runWindow(
        out: IntArray, width: Int, remaining: BooleanArray,
        left: Int, top: Int, ww: Int, wh: Int, write: Region,
    ) {
        var any = false
        loop@ for (y in max(top, write.top) until min(top + wh, write.bottom)) {
            for (x in max(left, write.left) until min(left + ww, write.right)) {
                if (remaining[y * width + x]) { any = true; break@loop }
            }
        }
        if (!any) return

        val windowPixels = IntArray(ww * wh)
        val windowHole = BooleanArray(ww * wh)
        for (y in 0 until wh) {
            val row = (top + y) * width + left
            for (x in 0 until ww) {
                windowPixels[y * ww + x] = out[row + x]
                windowHole[y * ww + x] = remaining[row + x]
            }
        }

        val inputPixels = resize(windowPixels, ww, wh, size, size)
        val inputHole = resizeMask(windowHole, ww, wh, size, size)
        val result = model.inpaint512(inputPixels, inputHole)
        // En fotos en blanco y negro el modelo a veces se inventa un tinte: se quita.
        if (isGrayscale(inputPixels, inputHole)) toGray(result, inputHole)
        val back = resize(result, size, size, ww, wh)

        for (y in max(top, write.top) until min(top + wh, write.bottom)) {
            for (x in max(left, write.left) until min(left + ww, write.right)) {
                val p = y * width + x
                if (!remaining[p]) continue
                out[p] = back[(y - top) * ww + (x - left)] or (0xFF shl 24)
                remaining[p] = false
            }
        }
    }

    /** True si los píxeles conocidos apenas tienen color (diferencia media entre canales pequeña). */
    private fun isGrayscale(pixels: IntArray, hole: BooleanArray): Boolean {
        var sum = 0L
        var count = 0
        for (i in pixels.indices) {
            if (hole[i]) continue
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            sum += max(r, max(g, b)) - min(r, min(g, b))
            count++
        }
        return count > 0 && sum.toFloat() / count < GRAY_CHROMA
    }

    private fun toGray(pixels: IntArray, hole: BooleanArray) {
        for (i in pixels.indices) {
            if (!hole[i]) continue
            val c = pixels[i]
            val l = (((c shr 16) and 0xFF) * 77 + ((c shr 8) and 0xFF) * 150 + (c and 0xFF) * 29) shr 8
            pixels[i] = (0xFF shl 24) or (l shl 16) or (l shl 8) or l
        }
    }

    private fun resize(pixels: IntArray, w: Int, h: Int, newW: Int, newH: Int): IntArray {
        if (w == newW && h == newH) return pixels.copyOf()
        val source = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
        val scaled = BitmapScaler.scaleHighQuality(source, newW, newH)
        val out = IntArray(newW * newH)
        scaled.getPixels(out, 0, newW, 0, 0, newW, newH)
        if (scaled !== source) scaled.recycle()
        source.recycle()
        return out
    }

    /**
     * Máscara escalada de forma conservadora: un píxel de destino es hueco si toca algún píxel de
     * hueco del origen, y se ensancha un poco para que no quede borde del objeto sin borrar.
     */
    private fun resizeMask(mask: BooleanArray, w: Int, h: Int, newW: Int, newH: Int): BooleanArray {
        val sx = w.toFloat() / newW
        val sy = h.toFloat() / newH
        val out = BooleanArray(newW * newH)
        for (y in 0 until newH) {
            val y0 = floor(y * sy).toInt().coerceIn(0, h - 1)
            val y1 = ceil((y + 1) * sy).toInt().coerceIn(y0 + 1, h)
            for (x in 0 until newW) {
                val x0 = floor(x * sx).toInt().coerceIn(0, w - 1)
                val x1 = ceil((x + 1) * sx).toInt().coerceIn(x0 + 1, w)
                var hit = false
                var yy = y0
                while (!hit && yy < y1) {
                    var xx = x0
                    while (xx < x1) {
                        if (mask[yy * w + xx]) { hit = true; break }
                        xx++
                    }
                    yy++
                }
                out[y * newW + x] = hit
            }
        }
        return dilate(out, newW, newH, MASK_DILATION)
    }

    private fun dilate(mask: BooleanArray, w: Int, h: Int, radius: Int): BooleanArray {
        if (radius <= 0) return mask
        val horizontal = BooleanArray(mask.size)
        for (y in 0 until h) for (x in 0 until w) {
            if (!mask[y * w + x]) continue
            for (xx in max(0, x - radius)..min(w - 1, x + radius)) horizontal[y * w + xx] = true
        }
        val out = BooleanArray(mask.size)
        for (y in 0 until h) for (x in 0 until w) {
            if (!horizontal[y * w + x]) continue
            for (yy in max(0, y - radius)..min(h - 1, y + radius)) out[yy * w + x] = true
        }
        return out
    }

    private companion object {
        /** Lado de la ventana respecto al tamaño de la zona a borrar. */
        const val CONTEXT_FACTOR = 2.6f
        /** Ventana mínima en píxeles de la imagen: para motas pequeñas basta con algo de contexto. */
        const val MIN_WINDOW = 192
        const val MASK_DILATION = 2
        /** Hasta cuánto se puede reducir una ventana antes de pasar a rellenar por trozos. */
        const val MAX_DOWNSCALE = 1.4f
        /** Celda que escribe cada ventana de 512 px al rellenar por trozos (el resto es contexto). */
        const val TILE_CELL = 240
        /** Diferencia media entre canales por debajo de la cual una zona se trata como gris. */
        const val GRAY_CHROMA = 8f
    }
}
