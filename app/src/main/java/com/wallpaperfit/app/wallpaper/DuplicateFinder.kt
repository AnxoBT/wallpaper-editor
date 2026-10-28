package com.wallpaperfit.app.wallpaper

import kotlin.math.abs

/**
 * Busca fotos repetidas: la misma imagen guardada dos veces, también en otro tamaño o con otra
 * compresión. Cada foto se reduce a una huella de 17 × 16 píxeles (sin Android, se prueba en JVM):
 *
 * - forma: si cada píxel es más claro que el de su derecha (256 bits), que no cambia al escalar;
 * - color: el color medio de cada píxel, para que dos fondos negros con algo distinto encima no
 *   cuenten como la misma foto.
 *
 * Dos fotos son la misma si se parecen en las dos cosas.
 */
object DuplicateFinder {

    const val WIDTH = 17
    const val HEIGHT = 16

    /** Huella de una foto: bits de forma y colores (r, g, b por píxel). */
    class Print(val bits: LongArray, val colors: IntArray)

    /** Huella de una foto ya reducida a [WIDTH] × [HEIGHT] píxeles ARGB, por filas. */
    fun print(pixels: IntArray): Print {
        require(pixels.size == WIDTH * HEIGHT)
        val gray = IntArray(pixels.size) { i ->
            val c = pixels[i]
            (c shr 16 and 0xFF) * 299 + (c shr 8 and 0xFF) * 587 + (c and 0xFF) * 114
        }
        val bits = LongArray(4)
        var bit = 0
        for (y in 0 until HEIGHT) {
            for (x in 0 until WIDTH - 1) {
                if (gray[y * WIDTH + x] > gray[y * WIDTH + x + 1]) bits[bit / 64] = bits[bit / 64] or (1L shl (bit % 64))
                bit++
            }
        }
        val colors = IntArray(pixels.size * 3)
        pixels.forEachIndexed { i, c ->
            colors[i * 3] = c shr 16 and 0xFF
            colors[i * 3 + 1] = c shr 8 and 0xFF
            colors[i * 3 + 2] = c and 0xFF
        }
        return Print(bits, colors)
    }

    /** Bits de forma distintos (0 a 256). */
    fun shapeDistance(a: Print, b: Print): Int = a.bits.indices.sumOf { java.lang.Long.bitCount(a.bits[it] xor b.bits[it]) }

    /** Diferencia media de color por canal (0 a 255). */
    fun colorDistance(a: Print, b: Print): Double = a.colors.indices.sumOf { abs(a.colors[it] - b.colors[it]) }.toDouble() / a.colors.size

    fun same(a: Print, b: Print): Boolean = shapeDistance(a, b) <= MAX_SHAPE && colorDistance(a, b) <= MAX_COLOR

    /**
     * Grupos de fotos iguales (de dos o más), en el orden de [prints]. Si A se parece a B y B a
     * C, las tres van juntas.
     */
    fun <K> groups(prints: List<Pair<K, Print>>): List<List<K>> {
        val parent = IntArray(prints.size) { it }
        fun root(i: Int): Int {
            var r = i
            while (parent[r] != r) r = parent[r]
            return r
        }
        for (i in prints.indices) {
            for (j in i + 1 until prints.size) {
                if (same(prints[i].second, prints[j].second)) parent[root(j)] = root(i)
            }
        }
        return prints.indices.groupBy(::root).values.filter { it.size > 1 }.map { group -> group.map { prints[it].first } }
    }

    /**
     * La que conviene guardar: la de más píxeles y, si son iguales, la más antigua (la que ya
     * tiene su sitio y sus etiquetas).
     */
    fun <T> original(items: List<T>, pixels: (T) -> Long, modified: (T) -> Long): T =
        items.sortedWith(compareByDescending(pixels).thenBy(modified)).first()

    /** Bits de forma que pueden cambiar (al escalar o comprimir) en la misma foto. */
    const val MAX_SHAPE = 12

    /** Diferencia media de color que se tolera en la misma foto. */
    const val MAX_COLOR = 10.0
}
