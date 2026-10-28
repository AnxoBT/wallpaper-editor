package com.wallpaperfit.app.processing

import android.graphics.Bitmap
import android.graphics.RectF
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizerOptionsInterface
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min

/**
 * Localiza textos en la imagen (firmas, nombres de autor, marcas de agua, títulos) con los
 * reconocedores de ML Kit incluidos en la app: alfabeto latino (con @, ©, números…), chino,
 * japonés y coreano. Funciona sin conexión y la imagen no sale del móvil.
 */
object TextDetector {

    private val scripts: List<TextRecognizerOptionsInterface> = listOf(
        TextRecognizerOptions.DEFAULT_OPTIONS,
        ChineseTextRecognizerOptions.Builder().build(),
        JapaneseTextRecognizerOptions.Builder().build(),
        KoreanTextRecognizerOptions.Builder().build(),
    )

    /** Rectángulos de cada línea de texto, en píxeles de [bitmap], sin duplicados entre alfabetos. */
    suspend fun detectLines(bitmap: Bitmap): List<RectF> {
        val image = InputImage.fromBitmap(bitmap, 0)
        val found = ArrayList<RectF>()
        for (options in scripts) {
            for (box in recognize(image, options)) {
                // Varios reconocedores leen el mismo texto: se queda el rectángulo que más cubre.
                val same = found.indexOfFirst { overlap(it, box) > DUPLICATE_OVERLAP }
                if (same < 0) {
                    found += box
                } else if (area(box) > area(found[same])) {
                    found[same] = box
                }
            }
        }
        return found
    }

    /**
     * Texto encontrado: rectángulo (texto y marcas cercanas), color de sus letras (0 = desconocido)
     * y altura de la línea de texto original (para medir márgenes).
     */
    class Found(val box: RectF, val color: Int, val lineHeight: Float)

    /**
     * Amplía cada texto encontrado con los trazos cercanos del mismo color que el OCR no lee:
     * títulos caligráficos o estilizados, firmas a mano, símbolos sueltos (©, ®, logotipos).
     */
    fun withNearbyMarks(bitmap: Bitmap, lines: List<RectF>): List<Found> {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        return lines.map { line -> expand(pixels, width, height, line) }
    }

    private fun expand(pixels: IntArray, width: Int, height: Int, line: RectF): Found {
        // Las líneas muy finas (subtítulos) se miden con un mínimo relativo a la imagen.
        val lh = max(line.height(), min(width, height) * MIN_LINE_FRACTION)
        val l0 = line.left.toInt().coerceIn(0, width - 1)
        val t0 = line.top.toInt().coerceIn(0, height - 1)
        val r0 = line.right.toInt().coerceIn(l0 + 1, width)
        val b0 = line.bottom.toInt().coerceIn(t0 + 1, height)
        // Si el texto apenas destaca del fondo, buscar su color fuera recogería fondo.
        val ink = TextMask.ink(pixels, width, l0, t0, r0, b0) ?: return Found(line, 0, line.height())

        // Zona de búsqueda alrededor del texto.
        val sl = (line.left - line.width() * SEARCH_X).toInt().coerceIn(0, width - 1)
        val sr = (line.right + line.width() * SEARCH_X).toInt().coerceIn(sl + 1, width)
        val st = (line.top - lh * SEARCH_Y).toInt().coerceIn(0, height - 1)
        val sb = (line.bottom + lh * SEARCH_Y).toInt().coerceIn(st + 1, height)
        val sw = sr - sl
        val sh = sb - st
        val mask = BooleanArray(sw * sh) { ink.matches(pixels[(st + it / sw) * width + sl + it % sw]) }

        // Componentes conexas del color del texto: se quedan las de tamaño de trazo, que no
        // tocan el borde de la zona (si lo tocan son fondo o un objeto grande).
        val marks = ArrayList<RectF>()
        val seen = BooleanArray(mask.size)
        val stack = IntArray(mask.size)
        val maxSide = lh * MAX_MARK_LINES
        for (start in mask.indices) {
            if (!mask[start] || seen[start]) continue
            var sp = 0
            stack[sp++] = start
            seen[start] = true
            var area = 0
            var cl = sw
            var ct = sh
            var cr = -1
            var cb = -1
            var touches = false
            while (sp > 0) {
                val p = stack[--sp]
                area++
                val x = p % sw
                val y = p / sw
                if (x < cl) cl = x
                if (x > cr) cr = x
                if (y < ct) ct = y
                if (y > cb) cb = y
                if (x == 0 || y == 0 || x == sw - 1 || y == sh - 1) touches = true
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = x + dx
                    val ny = y + dy
                    if (nx !in 0 until sw || ny !in 0 until sh) continue
                    val q = ny * sw + nx
                    if (mask[q] && !seen[q]) {
                        seen[q] = true
                        stack[sp++] = q
                    }
                }
            }
            if (touches || area < MIN_MARK_AREA) continue
            if (cr - cl + 1 > maxSide || cb - ct + 1 > maxSide) continue
            marks += RectF((sl + cl).toFloat(), (st + ct).toFloat(), (sl + cr + 1).toFloat(), (st + cb + 1).toFloat())
        }
        // Se añaden las marcas que quedan pegadas (a menos de [MARK_GAP] líneas) al texto o a otra
        // marca ya añadida: las sueltas y lejanas (brillos, detalles del dibujo) se ignoran.
        val union = RectF(line)
        var added = true
        while (added) {
            added = false
            val iterator = marks.iterator()
            while (iterator.hasNext()) {
                val mark = iterator.next()
                val gapX = max(0f, max(mark.left, union.left) - min(mark.right, union.right))
                val gapY = max(0f, max(mark.top, union.top) - min(mark.bottom, union.bottom))
                if (max(gapX, gapY) <= lh * MARK_GAP) {
                    union.union(mark)
                    iterator.remove()
                    added = true
                }
            }
        }
        return Found(union, ink.color, line.height())
    }

    private suspend fun recognize(image: InputImage, options: TextRecognizerOptionsInterface): List<RectF> =
        suspendCancellableCoroutine { continuation ->
            val recognizer = TextRecognition.getClient(options)
            recognizer.process(image)
                .addOnSuccessListener { text ->
                    val lines = text.textBlocks.flatMap { it.lines }.mapNotNull { line -> line.boundingBox?.let(::RectF) }
                    continuation.resume(lines)
                }
                .addOnFailureListener { continuation.resumeWithException(it) }
                .addOnCompleteListener { recognizer.close() }
        }

    /** Fracción del rectángulo más pequeño que queda dentro del otro. */
    private fun overlap(a: RectF, b: RectF): Float {
        val w = min(a.right, b.right) - max(a.left, b.left)
        val h = min(a.bottom, b.bottom) - max(a.top, b.top)
        if (w <= 0f || h <= 0f) return 0f
        return w * h / min(area(a), area(b)).coerceAtLeast(1f)
    }

    private fun area(r: RectF): Float = r.width() * r.height()

    private const val DUPLICATE_OVERLAP = 0.6f
    /** Búsqueda de marcas: hasta este número de alturas de línea arriba y abajo… */
    private const val SEARCH_Y = 10f
    /** …y esta fracción del ancho del texto a cada lado. */
    private const val SEARCH_X = 0.6f
    private const val MIN_MARK_AREA = 12
    /** Tamaño máximo de una marca, en alturas de línea (un título grande sobre el subtítulo). */
    private const val MAX_MARK_LINES = 12f
    /** Separación máxima, en alturas de línea, entre una marca y el texto para unirla. */
    private const val MARK_GAP = 2f
    /** Altura mínima de línea para medir la búsqueda, en fracción del lado corto. */
    private const val MIN_LINE_FRACTION = 0.025f
}
