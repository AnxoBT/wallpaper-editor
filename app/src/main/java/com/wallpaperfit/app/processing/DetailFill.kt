package com.wallpaperfit.app.processing

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Relleno inteligente a resolución completa sin pagar su coste.
 *
 * La síntesis por parches ([Inpainter]) se hace a una resolución de trabajo reducida, pero su
 * resultado no se amplía (eso lo dejaría borroso). Se usa solo el mapa de correspondencias:
 * para cada píxel del hueco se sabe de qué punto de la foto viene, y se copia ese píxel de la
 * imagen a resolución completa. Así se conservan detalles finos como estrellas, nubes o grano.
 *
 * Hay dos modos:
 * - [Mode.NATURAL] (sin uso: Expandir con textura ya no copia trozos, ver [TextureSynth]): la imagen de trabajo usa la mediana de cada celda, que borra los
 *   detalles de un píxel; la búsqueda elige zonas por estructura y al copiar a resolución completa
 *   las partículas, estrellas o el grano aparecen en su proporción real.
 * - [Mode.CLEAN] (Corrector y Expandir con fondo limpio): la imagen de trabajo usa la media y la síntesis no siembra textura;
 *   la búsqueda prefiere a propósito las zonas más limpias, así que al borrar una mota no se
 *   copia otra en su lugar. El color y el tono del fondo se mantienen.
 */
internal object DetailFill {

    enum class Mode { NATURAL, CLEAN }

    fun fill(
        pixels: IntArray,
        width: Int,
        height: Int,
        hole: BooleanArray,
        maxWorkSide: Int,
        mode: Mode,
        prior: Inpainter.ExpandPrior? = null,
        checkCancelled: () -> Unit,
    ): IntArray {
        val natural = mode == Mode.NATURAL
        val scale = min(1f, maxWorkSide.toFloat() / max(width, height))
        if (scale >= 1f) {
            val filled = Inpainter.inpaintWithMatches(pixels, width, height, hole, checkCancelled, preserveTexture = natural, prior = prior).pixels
            correctTone(filled, pixels, width, height, hole, checkCancelled, prior)
            return filled
        }

        val workWidth = max(1, (width * scale).roundToInt())
        val workHeight = max(1, (height * scale).roundToInt())
        val (cells, workHole) = downsample(pixels, width, height, hole, workWidth, workHeight, median = natural)
        // En modo natural la búsqueda trabaja sobre una mediana amplia: no "ve" partículas ni
        // objetos pequeños (hojas, motas), así que no las evita y al copiar aparecen en su
        // proporción real.
        val work = if (natural) medianFilterKnown(cells, workWidth, workHeight, workHole, GUIDE_MEDIAN_RADIUS) else cells
        val result = Inpainter.inpaintWithMatches(work, workWidth, workHeight, workHole, checkCancelled, preserveTexture = natural, prior = prior)
        checkCancelled()

        val matches = if (natural) {
            result.matches?.let { coherentBlocks(it, workWidth, workHeight, workHole, COPY_BLOCK) }
        } else {
            result.matches
        }
        val sx = workWidth.toFloat() / width
        val sy = workHeight.toFloat() / height
        val out = pixels.copyOf()
        // Cada píxel copia, sin remuestrear, el píxel que ocupa la misma posición dentro de la
        // celda de origen. No se mezclan celdas: promediar fuentes distintas apagaría los detalles
        // finos (una estrella mezclada con tres trozos de cielo vacío desaparece).
        for (y in 0 until height) {
            val cy = min(workHeight - 1, floor(y * sy).toInt())
            val dy = y - cy / sy
            for (x in 0 until width) {
                val p = y * width + x
                if (!hole[p]) continue
                val cx = min(workWidth - 1, floor(x * sx).toInt())
                val t = cy * workWidth + cx
                val match = matches?.get(t)?.takeIf { it >= 0 }
                out[p] = if (match == null) {
                    result.pixels[t]
                } else {
                    val dx = x - cx / sx
                    val srcX = ((match % workWidth) / sx + dx).roundToInt().coerceIn(0, width - 1)
                    val srcY = ((match / workWidth) / sy + dy).roundToInt().coerceIn(0, height - 1)
                    val src = srcY * width + srcX
                    if (hole[src]) result.pixels[match] else pixels[src] or (0xFF shl 24)
                }
            }
        }
        correctTone(out, pixels, width, height, hole, checkCancelled, prior)
        return out
    }

    /**
     * Ajuste final del tono general del relleno a un relleno liso calculado a muy baja
     * resolución (la síntesis ya se guía por esa referencia; esto corrige lo que quede tras
     * copiar los píxeles a resolución completa). Solo cambia la baja frecuencia: estrellas,
     * nubes y bordes se conservan.
     */
    private fun correctTone(
        filled: IntArray,
        original: IntArray,
        width: Int,
        height: Int,
        hole: BooleanArray,
        checkCancelled: () -> Unit,
        prior: Inpainter.ExpandPrior?,
    ) {
        val scale = min(1f, TONE_SIDE.toFloat() / max(width, height))
        val toneWidth = max(1, (width * scale).roundToInt())
        val toneHeight = max(1, (height * scale).roundToInt())
        val (small, smallHole) = downsample(original, width, height, hole, toneWidth, toneHeight)
        if (smallHole.none { it }) return
        val reference = Inpainter.inpaintWithMatches(small, toneWidth, toneHeight, smallHole, checkCancelled, prior = prior).pixels
        // Mediana por celda: los objetos pequeños copiados (hojas, motas) no cuentan como tono.
        val current = downsample(filled, width, height, BooleanArray(width * height), toneWidth, toneHeight).first
        val radius = max(2, (max(toneWidth, toneHeight) * TONE_RADIUS_FRACTION).roundToInt())

        // Celdas que se apartan mucho del tono de su entorno (objetos más grandes que una celda)
        // se excluyen al medir el tono; si no, la corrección dibujaría un halo a su alrededor.
        val luma = FloatArray(current.size) { lumaOf(current[it]) }
        val surroundings = luma.copyOf().also { blur(it, toneWidth, toneHeight, radius) }
        val weights = FloatArray(current.size) { if (abs(luma[it] - surroundings[it]) < TONE_OUTLIER) 1f else 0f }
        val blurredWeights = weights.copyOf().also { blur(it, toneWidth, toneHeight, radius) }

        val delta = Array(3) { c ->
            val target = FloatArray(reference.size) { ((reference[it] shr (16 - 8 * c)) and 0xFF).toFloat() }
            val actual = FloatArray(current.size) { ((current[it] shr (16 - 8 * c)) and 0xFF).toFloat() * weights[it] }
            blur(target, toneWidth, toneHeight, radius)
            blur(actual, toneWidth, toneHeight, radius)
            FloatArray(target.size) { i ->
                if (blurredWeights[i] > 0.05f) target[i] - actual[i] / blurredWeights[i] else 0f
            }
        }

        val sx = toneWidth.toFloat() / width
        val sy = toneHeight.toFloat() / height
        for (y in 0 until height) {
            val fy = ((y + 0.5f) * sy - 0.5f).coerceIn(0f, (toneHeight - 1).toFloat())
            val y0 = fy.toInt()
            val y1 = min(toneHeight - 1, y0 + 1)
            val ty = fy - y0
            for (x in 0 until width) {
                val p = y * width + x
                if (!hole[p]) continue
                val fx = ((x + 0.5f) * sx - 0.5f).coerceIn(0f, (toneWidth - 1).toFloat())
                val x0 = fx.toInt()
                val x1 = min(toneWidth - 1, x0 + 1)
                val tx = fx - x0
                val a = y0 * toneWidth + x0
                val b = y0 * toneWidth + x1
                val c = y1 * toneWidth + x0
                val d = y1 * toneWidth + x1
                val color = filled[p]
                var result = color and (0xFF shl 24)
                for (ch in 0..2) {
                    val field = delta[ch]
                    val top = field[a] + (field[b] - field[a]) * tx
                    val bottom = field[c] + (field[d] - field[c]) * tx
                    val shift = top + (bottom - top) * ty
                    val value = (((color shr (16 - 8 * ch)) and 0xFF) + shift).roundToInt().coerceIn(0, 255)
                    result = result or (value shl (16 - 8 * ch))
                }
                filled[p] = result
            }
        }
    }

    /**
     * Agrupa el hueco en bloques de [block] × [block] celdas y hace que todo el bloque copie una
     * zona contigua de la foto (la elegida para su centro). Así un objeto que cae dentro del
     * bloque se copia entero en lugar de a trozos de celdas de orígenes distintos.
     */
    private fun coherentBlocks(matches: IntArray, width: Int, height: Int, hole: BooleanArray, block: Int): IntArray {
        val result = matches.copyOf()
        for (cy in 0 until height) {
            val by = min(height - 1, cy / block * block + block / 2)
            for (cx in 0 until width) {
                val t = cy * width + cx
                if (!hole[t]) continue
                val bx = min(width - 1, cx / block * block + block / 2)
                val center = by * width + bx
                val m = if (hole[center]) matches[center] else -1
                if (m < 0) continue
                val sx = cx + (m % width - bx)
                val sy = cy + (m / width - by)
                if (sx in 0 until width && sy in 0 until height && !hole[sy * width + sx]) result[t] = sy * width + sx
            }
        }
        return result
    }

    /** Mediana (por luminancia) de cada píxel conocido sobre sus vecinos conocidos. */
    private fun medianFilterKnown(pixels: IntArray, width: Int, height: Int, hole: BooleanArray, radius: Int): IntArray {
        val size = (2 * radius + 1) * (2 * radius + 1)
        val colors = IntArray(size)
        val keys = IntArray(size)
        val out = pixels.copyOf()
        for (y in 0 until height) {
            for (x in 0 until width) {
                val p = y * width + x
                if (hole[p]) continue
                var n = 0
                for (yy in max(0, y - radius)..min(height - 1, y + radius)) {
                    for (xx in max(0, x - radius)..min(width - 1, x + radius)) {
                        val q = yy * width + xx
                        if (hole[q]) continue
                        val c = pixels[q]
                        colors[n] = c
                        keys[n] = ((c shr 16) and 0xFF) * 77 + ((c shr 8) and 0xFF) * 150 + (c and 0xFF) * 29
                        n++
                    }
                }
                out[p] = medianColor(colors, keys, n) or (0xFF shl 24)
            }
        }
        return out
    }

    private fun lumaOf(c: Int): Float = (((c shr 16) and 0xFF) * 0.299f + ((c shr 8) and 0xFF) * 0.587f + (c and 0xFF) * 0.114f)

    /** Desenfoque de caja separable, dos pasadas (aprox. gaussiano), con sumas acumuladas. */
    private fun blur(values: FloatArray, width: Int, height: Int, radius: Int) {
        val line = FloatArray(max(width, height))
        val out = FloatArray(max(width, height))
        repeat(2) {
            for (y in 0 until height) {
                for (x in 0 until width) line[x] = values[y * width + x]
                boxLine(line, out, width, radius)
                for (x in 0 until width) values[y * width + x] = out[x]
            }
            for (x in 0 until width) {
                for (y in 0 until height) line[y] = values[y * width + x]
                boxLine(line, out, height, radius)
                for (y in 0 until height) values[y * width + x] = out[y]
            }
        }
    }

    private fun boxLine(line: FloatArray, out: FloatArray, n: Int, radius: Int) {
        val prefix = DoubleArray(n + 1)
        for (i in 0 until n) prefix[i + 1] = prefix[i] + line[i]
        for (i in 0 until n) {
            val from = max(0, i - radius)
            val to = min(n - 1, i + radius)
            out[i] = ((prefix[to + 1] - prefix[from]) / (to - from + 1)).toFloat()
        }
    }

    private const val GUIDE_MEDIAN_RADIUS = 3
    private const val COPY_BLOCK = 6
    private const val TONE_SIDE = 320
    /** Diferencia de luminancia a partir de la cual una celda se considera un objeto, no tono. */
    private const val TONE_OUTLIER = 18f
    private const val TONE_RADIUS_FRACTION = 0.04f

    /**
     * Imagen de trabajo para la búsqueda. Con [median], cada celda toma el color mediano (por
     * luminancia) de sus píxeles conocidos: elimina puntos sueltos (estrellas, motas, grano) y
     * conserva bordes y degradados. Sin [median], la media: las celdas con motas quedan algo
     * distintas y la búsqueda tiende a evitarlas. Una celda es hueco si contiene algún píxel
     * del hueco.
     */
    private fun downsample(
        pixels: IntArray,
        width: Int,
        height: Int,
        hole: BooleanArray,
        workWidth: Int,
        workHeight: Int,
        median: Boolean = true,
    ): Pair<IntArray, BooleanArray> {
        val work = IntArray(workWidth * workHeight)
        val workHole = BooleanArray(workWidth * workHeight)
        val maxCell = (ceil(width.toFloat() / workWidth).toInt() + 1) * (ceil(height.toFloat() / workHeight).toInt() + 1)
        val colors = IntArray(maxCell)
        val keys = IntArray(maxCell)
        for (wy in 0 until workHeight) {
            val yStart = wy * height / workHeight
            val yEnd = max(yStart + 1, (wy + 1) * height / workHeight)
            for (wx in 0 until workWidth) {
                val xStart = wx * width / workWidth
                val xEnd = max(xStart + 1, (wx + 1) * width / workWidth)
                var n = 0
                var anyHole = false
                for (y in yStart until min(height, yEnd)) {
                    val row = y * width
                    for (x in xStart until min(width, xEnd)) {
                        val p = row + x
                        if (hole[p]) {
                            anyHole = true
                        } else if (n < maxCell) {
                            val c = pixels[p]
                            colors[n] = c
                            keys[n] = ((c shr 16) and 0xFF) * 77 + ((c shr 8) and 0xFF) * 150 + (c and 0xFF) * 29
                            n++
                        }
                    }
                }
                val q = wy * workWidth + wx
                workHole[q] = anyHole
                work[q] = when {
                    n == 0 -> 0xFF shl 24
                    median -> medianColor(colors, keys, n) or (0xFF shl 24)
                    else -> meanColor(colors, n)
                }
            }
        }
        return work to workHole
    }

    private fun meanColor(colors: IntArray, n: Int): Int {
        var r = 0
        var g = 0
        var b = 0
        for (i in 0 until n) {
            val c = colors[i]
            r += (c shr 16) and 0xFF
            g += (c shr 8) and 0xFF
            b += c and 0xFF
        }
        return (0xFF shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
    }

    /** Ordenación por inserción (las celdas tienen pocas decenas de píxeles) y elemento central. */
    private fun medianColor(colors: IntArray, keys: IntArray, n: Int): Int {
        for (i in 1 until n) {
            val key = keys[i]
            val color = colors[i]
            var j = i - 1
            while (j >= 0 && keys[j] > key) {
                keys[j + 1] = keys[j]
                colors[j + 1] = colors[j]
                j--
            }
            keys[j + 1] = key
            colors[j + 1] = color
        }
        return colors[n / 2]
    }
}
