package com.wallpaperfit.app.processing

import java.util.Random
import java.util.stream.IntStream
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Relleno inteligente ("content-aware fill") por síntesis de parches, en la línea de
 * Wexler et al. 2007 + PatchMatch (Barnes et al. 2009), que es la familia de algoritmos que usan
 * herramientas como Expandir y Corrector de Snapseed:
 *
 * 1. Se construye una pirámide de resoluciones de la imagen y de la máscara del hueco.
 * 2. En el nivel más pequeño el hueco se rellena de forma aproximada (difusión desde el borde).
 * 3. En cada nivel se alterna (EM):
 *    - PatchMatch: para cada parche que toca el hueco se busca el parche más parecido de la zona
 *      conocida, mediante propagación entre vecinos y búsqueda aleatoria.
 *    - Votación: cada píxel del hueco se recalcula como la media ponderada de los píxeles que le
 *      proponen todos los parches que lo cubren.
 * 4. El resultado se sube al nivel siguiente como punto de partida.
 *
 * Todo es determinista (semillas fijas) para que la vista previa y la exportación coincidan.
 */
object Inpainter {

    private const val RADIUS = 3
    private const val MIN_LEVEL_SIDE = 24
    private const val SIGMA = 22f
    private const val LARGE_LEVEL_PIXELS = 160_000

    /**
     * Devuelve una copia de [pixels] (ARGB, [width] × [height]) con los píxeles marcados en [hole]
     * sintetizados a partir del resto de la imagen. [checkCancelled] se llama entre iteraciones y
     * puede lanzar una excepción para abortar.
     */
    fun inpaint(
        pixels: IntArray,
        width: Int,
        height: Int,
        hole: BooleanArray,
        checkCancelled: () -> Unit = {},
    ): IntArray = inpaintWithMatches(pixels, width, height, hole, checkCancelled).pixels

    /**
     * Resultado del relleno: los píxeles y, para cada píxel del hueco (y su entorno), el índice
     * del centro del parche fuente elegido (-1 si no tiene). Con este mapa se puede volver a
     * construir el relleno copiando píxeles de una versión de mayor resolución de la imagen.
     */
    class Result(val pixels: IntArray, val matches: IntArray?)

    /**
     * Preferencias de Expandir, en fracciones del lienzo: la foto ocupa [left]..[right] ×
     * [top]..[bottom] y el resto es hueco.
     * - [position]: cuánto se prefiere copiar de la parte de la foto más cercana (lo de arriba
     *   continúa lo de arriba: cielo con cielo, suelo con suelo).
     * - [structure]: cuánto se evitan, lejos de la foto, los trozos con bordes y formas (montañas,
     *   columnas, personas), que repetidos quedan falsos; ahí se prefiere fondo.
     */
    class ExpandPrior(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val position: Float = 1f,
        val structure: Float = 1f,
    )

    /**
     * [preserveTexture] siembra textura en cada nivel y vota con el mejor parche en vez de con la
     * media: el relleno conserva nubes, grano o estrellas en su densidad real en lugar de quedar
     * liso (con la media, la búsqueda acaba prefiriendo siempre las zonas más planas). Para que
     * los trozos copiados no traigan cada uno su tono (manchas en los degradados), en cada
     * iteración el tono general se corrige hacia un relleno liso de referencia.
     */
    fun inpaintWithMatches(
        pixels: IntArray,
        width: Int,
        height: Int,
        hole: BooleanArray,
        checkCancelled: () -> Unit = {},
        preserveTexture: Boolean = false,
        prior: ExpandPrior? = null,
    ): Result {
        if (hole.none { it }) return Result(pixels.copyOf(), null)

        val finest = Level.fromPixels(pixels, width, height, hole)
        if (finest.sources.isEmpty()) return Result(diffusionFill(finest).toPixels(pixels), null)

        val pyramid = arrayListOf(finest)
        while (true) {
            val current = pyramid.last()
            if (min(current.w, current.h) / 2 < MIN_LEVEL_SIDE) break
            val next = current.downsample()
            if (next.sources.isEmpty() || next.holeCount == 0) break
            pyramid += next
        }

        // Referencia de tono: relleno liso (media de parches) a baja resolución. La síntesis con
        // textura se corrige hacia ella en cada iteración para que copie trozos del tono correcto.
        val toneReference = if (preserveTexture) buildToneReference(pyramid, checkCancelled, prior) else null

        var previous: Level? = null
        var previousNnf: IntArray? = null
        for (index in pyramid.indices.reversed()) {
            checkCancelled()
            val level = pyramid[index]
            val coarsest = previous == null
            if (coarsest) {
                diffusionFill(level)
            } else {
                level.upsampleHoleFrom(previous!!)
            }
            val random = Random((SEED + index).toLong())
            val textured = preserveTexture
            if (textured && !coarsest) injectTexture(level, random)
            val targets = level.targetCenters()
            val step = if (level.w * level.h > LARGE_LEVEL_PIXELS) 2 else 1
            if (prior != null) level.applyPrior(prior, targets, step)
            val nnf = IntArray(level.w * level.h) { -1 }
            for (t in targets) {
                nnf[t] = previousNnf?.let { upsampleMatch(level, previous!!, it, t, random) }
                    ?: level.randomSource(random)
            }
            val dist = IntArray(level.w * level.h)
            val emIterations = when {
                coarsest -> 6
                index == 0 -> if (textured) 3 else 2
                else -> 3
            }
            val pmIterations = if (coarsest) 4 else 2

            repeat(emIterations) { em ->
                checkCancelled()
                computeDistances(level, targets, nnf, dist, step)
                repeat(pmIterations) { pm ->
                    patchMatchPass(level, targets, nnf, dist, step, reverse = pm % 2 == 1, seed = SEED * 31 + index * 97 + em * 13 + pm)
                }
                vote(level, nnf, dist, step, bestOnly = textured)
                if (textured && toneReference != null) pullTone(level, toneReference)
            }
            previous = level
            previousNnf = nnf
        }
        return Result(finest.toPixels(pixels), previousNnf)
    }

    private const val SEED = 0x1A2B

    /** Error por muestra equivalente a ~10 niveles de diferencia en cada canal. */
    private const val PRIOR_UNIT = 300f
    /** Distancia (fracción del lado corto) a la que la preferencia de posición actúa del todo. */
    private const val POSITION_RAMP = 0.08f
    /** Alejarse esta fracción del lado corto del punto a continuar cuesta una unidad. */
    private const val POSITION_SCALE = 0.25f
    /** Entre estas distancias a la foto se pasa de no evitar estructura a evitarla del todo. */
    private const val STRUCTURE_START = 0.04f
    private const val STRUCTURE_END = 0.3f
    /** Gradiente medio de un parche que cuesta una unidad lejos de la foto. */
    private const val STRUCTURE_SCALE = 8f

    private fun smooth(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
    private const val TONE_REFERENCE_SIDE = 256
    private const val TONE_RADIUS_FRACTION = 0.03f

    // region Referencia de tono

    private class ToneReference(val w: Int, val h: Int, val channels: Array<FloatArray>)

    private fun buildToneReference(pyramid: List<Level>, checkCancelled: () -> Unit, prior: ExpandPrior?): ToneReference {
        val level = pyramid.firstOrNull { max(it.w, it.h) <= TONE_REFERENCE_SIDE } ?: pyramid.last()
        val pixels = IntArray(level.w * level.h) { (0xFF shl 24) or (level.r[it] shl 16) or (level.g[it] shl 8) or level.b[it] }
        val smooth = inpaintWithMatches(pixels, level.w, level.h, level.hole, checkCancelled, preserveTexture = false, prior = prior).pixels
        return ToneReference(
            level.w,
            level.h,
            Array(3) { c -> FloatArray(smooth.size) { ((smooth[it] shr (16 - 8 * c)) and 0xFF).toFloat() } },
        )
    }

    /**
     * Ajusta la baja frecuencia del hueco de [level] a la referencia lisa: se comparan ambas en
     * una rejilla pequeña, desenfocadas, y la diferencia se suma (interpolada) a los píxeles del
     * hueco. El detalle y la textura no cambian.
     */
    private fun pullTone(level: Level, reference: ToneReference) {
        val gw = min(level.w, reference.w)
        val gh = min(level.h, reference.h)
        val radius = max(1, (max(gw, gh) * TONE_RADIUS_FRACTION).toInt())
        val current = Array(3) { FloatArray(gw * gh) }
        val counts = IntArray(gw * gh)
        for (y in 0 until level.h) {
            val gy = min(gh - 1, y * gh / level.h)
            for (x in 0 until level.w) {
                val g = gy * gw + min(gw - 1, x * gw / level.w)
                val p = y * level.w + x
                current[0][g] += level.r[p].toFloat()
                current[1][g] += level.g[p].toFloat()
                current[2][g] += level.b[p].toFloat()
                counts[g]++
            }
        }
        val delta = Array(3) { c ->
            val cur = current[c]
            for (i in cur.indices) cur[i] /= max(1, counts[i])
            val target = FloatArray(gw * gh) { i ->
                val rx = ((i % gw + 0.5f) * reference.w / gw).toInt().coerceIn(0, reference.w - 1)
                val ry = ((i / gw + 0.5f) * reference.h / gh).toInt().coerceIn(0, reference.h - 1)
                reference.channels[c][ry * reference.w + rx]
            }
            boxBlur(cur, gw, gh, radius)
            boxBlur(target, gw, gh, radius)
            FloatArray(gw * gh) { target[it] - cur[it] }
        }
        val sx = gw.toFloat() / level.w
        val sy = gh.toFloat() / level.h
        for (y in 0 until level.h) {
            val fy = ((y + 0.5f) * sy - 0.5f).coerceIn(0f, (gh - 1).toFloat())
            val y0 = fy.toInt()
            val y1 = min(gh - 1, y0 + 1)
            val ty = fy - y0
            for (x in 0 until level.w) {
                val p = y * level.w + x
                if (!level.hole[p]) continue
                val fx = ((x + 0.5f) * sx - 0.5f).coerceIn(0f, (gw - 1).toFloat())
                val x0 = fx.toInt()
                val x1 = min(gw - 1, x0 + 1)
                val tx = fx - x0
                val a = y0 * gw + x0
                val b = y0 * gw + x1
                val c = y1 * gw + x0
                val d = y1 * gw + x1
                for (ch in 0..2) {
                    val field = delta[ch]
                    val top = field[a] + (field[b] - field[a]) * tx
                    val bottom = field[c] + (field[d] - field[c]) * tx
                    val shift = (top + (bottom - top) * ty).toInt()
                    when (ch) {
                        0 -> level.r[p] = (level.r[p] + shift).coerceIn(0, 255)
                        1 -> level.g[p] = (level.g[p] + shift).coerceIn(0, 255)
                        else -> level.b[p] = (level.b[p] + shift).coerceIn(0, 255)
                    }
                }
            }
        }
    }

    /** Desenfoque de caja separable, dos pasadas, con sumas acumuladas. */
    private fun boxBlur(values: FloatArray, width: Int, height: Int, radius: Int) {
        val line = FloatArray(max(width, height))
        val prefix = DoubleArray(max(width, height) + 1)
        fun pass(n: Int, read: (Int) -> Float, write: (Int, Float) -> Unit) {
            for (i in 0 until n) line[i] = read(i)
            for (i in 0 until n) prefix[i + 1] = prefix[i] + line[i]
            for (i in 0 until n) {
                val from = max(0, i - radius)
                val to = min(n - 1, i + radius)
                write(i, ((prefix[to + 1] - prefix[from]) / (to - from + 1)).toFloat())
            }
        }
        repeat(2) {
            for (y in 0 until height) pass(width, { values[y * width + it] }, { i, v -> values[y * width + i] = v })
            for (x in 0 until width) pass(height, { values[it * width + x] }, { i, v -> values[i * width + x] = v })
        }
    }

    // endregion

    // region Niveles

    private class Level(
        val w: Int,
        val h: Int,
        val r: IntArray,
        val g: IntArray,
        val b: IntArray,
        val hole: BooleanArray,
    ) {
        val holeCount = hole.count { it }
        val sourceOk = BooleanArray(w * h)
        val sources: IntArray

        init {
            // Un centro es fuente válida si todo su parche cae dentro de la imagen y fuera del hueco.
            val stride = w + 1
            val integral = IntArray(stride * (h + 1))
            for (y in 0 until h) {
                var rowSum = 0
                for (x in 0 until w) {
                    if (hole[y * w + x]) rowSum++
                    integral[(y + 1) * stride + x + 1] = integral[y * stride + x + 1] + rowSum
                }
            }
            var count = 0
            for (y in RADIUS until h - RADIUS) {
                for (x in RADIUS until w - RADIUS) {
                    val x0 = x - RADIUS
                    val y0 = y - RADIUS
                    val x1 = x + RADIUS + 1
                    val y1 = y + RADIUS + 1
                    val inHole = integral[y1 * stride + x1] - integral[y0 * stride + x1] -
                        integral[y1 * stride + x0] + integral[y0 * stride + x0]
                    if (inHole == 0) {
                        sourceOk[y * w + x] = true
                        count++
                    }
                }
            }
            sources = IntArray(count)
            var i = 0
            for (p in sourceOk.indices) if (sourceOk[p]) sources[i++] = p
        }

        fun randomSource(random: Random): Int = sources[random.nextInt(sources.size)]

        // Preferencias de Expandir (ver [ExpandPrior]); null si no hay.
        private var anchorX: FloatArray? = null
        private var anchorY: FloatArray? = null
        private var positionWeight: FloatArray? = null
        private var structureWeight: FloatArray? = null
        private var patchStructure: FloatArray? = null

        /**
         * Precalcula, para cada parche destino, el punto de la foto que continúa (el más cercano)
         * y cuánto pesan las preferencias según lo lejos que esté; y, para cada fuente, cuánta
         * estructura (bordes) tiene su parche.
         */
        fun applyPrior(prior: ExpandPrior, targets: IntArray, step: Int) {
            val short = min(w, h).toFloat()
            val samples = ((2 * RADIUS) / step + 1).let { it * it }
            val unit = PRIOR_UNIT * samples
            val l = prior.left * w
            val t = prior.top * h
            val r = prior.right * w - 1
            val bm = prior.bottom * h - 1
            val ax = FloatArray(w * h)
            val ay = FloatArray(w * h)
            val pw = FloatArray(w * h)
            val sw = FloatArray(w * h)
            for (p in targets) {
                val x = (p % w).toFloat()
                val y = (p / w).toFloat()
                val cx = x.coerceIn(l, max(l, r))
                val cy = y.coerceIn(t, max(t, bm))
                ax[p] = cx
                ay[p] = cy
                val out = hypot(x - cx, y - cy) / short
                pw[p] = prior.position * unit * smooth(0f, POSITION_RAMP, out) / (POSITION_SCALE * short)
                sw[p] = prior.structure * unit * smooth(STRUCTURE_START, STRUCTURE_END, out) / STRUCTURE_SCALE
            }
            anchorX = ax
            anchorY = ay
            positionWeight = pw
            structureWeight = sw
            patchStructure = structureMap()
        }

        /** Gradiente medio de luminancia en el parche centrado en cada fuente. */
        private fun structureMap(): FloatArray {
            val grad = FloatArray(w * h)
            for (y in 0 until h) for (x in 0 until w) {
                val p = y * w + x
                if (hole[p]) continue
                val l = luma(p)
                val gx = if (x + 1 < w && !hole[p + 1]) abs(luma(p + 1) - l) else 0
                val gy = if (y + 1 < h && !hole[p + w]) abs(luma(p + w) - l) else 0
                grad[p] = (gx + gy).toFloat()
            }
            val stride = w + 1
            val integral = DoubleArray(stride * (h + 1))
            for (y in 0 until h) {
                var row = 0.0
                for (x in 0 until w) {
                    row += grad[y * w + x]
                    integral[(y + 1) * stride + x + 1] = integral[y * stride + x + 1] + row
                }
            }
            val out = FloatArray(w * h)
            val area = (2 * RADIUS + 1) * (2 * RADIUS + 1)
            for (p in sources) {
                val x0 = p % w - RADIUS
                val y0 = p / w - RADIUS
                val x1 = x0 + 2 * RADIUS + 1
                val y1 = y0 + 2 * RADIUS + 1
                val sum = integral[y1 * stride + x1] - integral[y0 * stride + x1] - integral[y1 * stride + x0] + integral[y0 * stride + x0]
                out[p] = (sum / area).toFloat()
            }
            return out
        }

        private fun luma(p: Int): Int = (r[p] * 77 + g[p] * 150 + b[p] * 29) shr 8

        /** Coste extra de usar la fuente [s] para el destino [t] según las preferencias. */
        fun priorCost(t: Int, s: Int): Int {
            val pw = positionWeight ?: return 0
            val dx = (s % w) - anchorX!![t]
            val dy = (s / w) - anchorY!![t]
            val position = pw[t] * sqrt(dx * dx + dy * dy)
            val structure = structureWeight!![t] * patchStructure!![s]
            return (position + structure).toInt()
        }

        /** Centros de parche que tocan el hueco (hueco dilatado por el radio del parche). */
        fun targetCenters(): IntArray {
            val mark = BooleanArray(w * h)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    if (!hole[y * w + x]) continue
                    for (yy in max(0, y - RADIUS)..min(h - 1, y + RADIUS)) {
                        val row = yy * w
                        for (xx in max(0, x - RADIUS)..min(w - 1, x + RADIUS)) mark[row + xx] = true
                    }
                }
            }
            val result = IntArray(mark.count { it })
            var i = 0
            for (p in mark.indices) if (mark[p]) result[i++] = p
            return result
        }

        /** Mitad de resolución; un píxel es hueco si lo es cualquiera de sus cuatro hijos. */
        fun downsample(): Level {
            val nw = max(1, w / 2)
            val nh = max(1, h / 2)
            val nr = IntArray(nw * nh)
            val ng = IntArray(nw * nh)
            val nb = IntArray(nw * nh)
            val nHole = BooleanArray(nw * nh)
            for (y in 0 until nh) {
                for (x in 0 until nw) {
                    var sr = 0
                    var sg = 0
                    var sb = 0
                    var known = 0
                    var anyHole = false
                    for (dy in 0..1) {
                        val yy = min(h - 1, y * 2 + dy)
                        for (dx in 0..1) {
                            val p = yy * w + min(w - 1, x * 2 + dx)
                            if (hole[p]) {
                                anyHole = true
                            } else {
                                sr += r[p]; sg += g[p]; sb += b[p]; known++
                            }
                        }
                    }
                    val q = y * nw + x
                    nHole[q] = anyHole
                    if (known > 0) {
                        nr[q] = sr / known; ng[q] = sg / known; nb[q] = sb / known
                    }
                }
            }
            return Level(nw, nh, nr, ng, nb, nHole)
        }

        /** Inicializa el hueco con la interpolación bilineal del nivel inferior ya resuelto. */
        fun upsampleHoleFrom(coarse: Level) {
            val sx = coarse.w.toFloat() / w
            val sy = coarse.h.toFloat() / h
            for (y in 0 until h) {
                val fy = ((y + 0.5f) * sy - 0.5f).coerceIn(0f, (coarse.h - 1).toFloat())
                val y0 = fy.toInt()
                val y1 = min(coarse.h - 1, y0 + 1)
                val ty = fy - y0
                for (x in 0 until w) {
                    val p = y * w + x
                    if (!hole[p]) continue
                    val fx = ((x + 0.5f) * sx - 0.5f).coerceIn(0f, (coarse.w - 1).toFloat())
                    val x0 = fx.toInt()
                    val x1 = min(coarse.w - 1, x0 + 1)
                    val tx = fx - x0
                    val a = y0 * coarse.w + x0
                    val bb = y0 * coarse.w + x1
                    val c = y1 * coarse.w + x0
                    val d = y1 * coarse.w + x1
                    r[p] = bilerp(coarse.r, a, bb, c, d, tx, ty)
                    g[p] = bilerp(coarse.g, a, bb, c, d, tx, ty)
                    b[p] = bilerp(coarse.b, a, bb, c, d, tx, ty)
                }
            }
        }

        fun toPixels(original: IntArray): IntArray {
            val out = original.copyOf()
            for (p in out.indices) {
                if (hole[p]) out[p] = (0xFF shl 24) or (r[p] shl 16) or (g[p] shl 8) or b[p]
            }
            return out
        }

        companion object {
            fun fromPixels(pixels: IntArray, w: Int, h: Int, hole: BooleanArray): Level {
                val r = IntArray(w * h)
                val g = IntArray(w * h)
                val b = IntArray(w * h)
                for (p in pixels.indices) {
                    val c = pixels[p]
                    r[p] = (c shr 16) and 0xFF
                    g[p] = (c shr 8) and 0xFF
                    b[p] = c and 0xFF
                }
                return Level(w, h, r, g, b, hole.copyOf())
            }

            private fun bilerp(ch: IntArray, a: Int, b: Int, c: Int, d: Int, tx: Float, ty: Float): Int {
                val top = ch[a] + (ch[b] - ch[a]) * tx
                val bottom = ch[c] + (ch[d] - ch[c]) * tx
                return (top + (bottom - top) * ty).toInt().coerceIn(0, 255)
            }
        }
    }

    /** Relleno inicial por capas desde el borde del hueco (media de los vecinos ya conocidos). */
    private fun diffusionFill(level: Level): Level {
        val w = level.w
        val h = level.h
        val known = BooleanArray(w * h) { !level.hole[it] }
        var pending = (0 until w * h).filter { level.hole[it] }
        while (pending.isNotEmpty()) {
            val resolved = ArrayList<Int>()
            val next = ArrayList<Int>()
            for (p in pending) {
                val x = p % w
                val y = p / w
                var sr = 0
                var sg = 0
                var sb = 0
                var n = 0
                for (yy in max(0, y - 1)..min(h - 1, y + 1)) {
                    for (xx in max(0, x - 1)..min(w - 1, x + 1)) {
                        val q = yy * w + xx
                        if (known[q]) {
                            sr += level.r[q]; sg += level.g[q]; sb += level.b[q]; n++
                        }
                    }
                }
                if (n > 0) {
                    level.r[p] = sr / n; level.g[p] = sg / n; level.b[p] = sb / n
                    resolved += p
                } else {
                    next += p
                }
            }
            if (resolved.isEmpty()) {
                // Imagen sin ningún píxel conocido: gris neutro.
                for (p in next) {
                    level.r[p] = 128; level.g[p] = 128; level.b[p] = 128
                }
                break
            }
            for (p in resolved) known[p] = true
            pending = next
        }
        return level
    }

    // endregion

    // region PatchMatch

    private fun upsampleMatch(fine: Level, coarse: Level, coarseNnf: IntArray, t: Int, random: Random): Int {
        val x = t % fine.w
        val y = t / fine.w
        val cx = min(coarse.w - 1, x / 2)
        val cy = min(coarse.h - 1, y / 2)
        val match = coarseNnf[cy * coarse.w + cx]
        if (match < 0) return fine.randomSource(random)
        val sx = (match % coarse.w) * 2 + (x - cx * 2)
        val sy = (match / coarse.w) * 2 + (y - cy * 2)
        if (sx !in 0 until fine.w || sy !in 0 until fine.h) return fine.randomSource(random)
        val s = sy * fine.w + sx
        return if (fine.sourceOk[s]) s else fine.randomSource(random)
    }

    /** Suma de diferencias al cuadrado entre el parche destino [t] y el parche fuente [s]. */
    private fun distance(level: Level, t: Int, s: Int, limit: Int, step: Int): Int {
        val w = level.w
        val h = level.h
        val tx = t % w
        val ty = t / w
        val sx = s % w
        val sy = s / w
        val r = level.r
        val g = level.g
        val b = level.b
        var sum = level.priorCost(t, s)
        if (sum >= limit) return sum
        var dy = -RADIUS
        while (dy <= RADIUS) {
            val yt = ty + dy
            if (yt in 0 until h) {
                val rowT = yt * w
                val rowS = (sy + dy) * w + sx
                var dx = -RADIUS
                while (dx <= RADIUS) {
                    val xt = tx + dx
                    if (xt in 0 until w) {
                        val a = rowT + xt
                        val c = rowS + dx
                        val dr = r[a] - r[c]
                        val dg = g[a] - g[c]
                        val db = b[a] - b[c]
                        sum += dr * dr + dg * dg + db * db
                    }
                    dx += step
                }
                if (sum >= limit) return sum
            }
            dy += step
        }
        return sum
    }

    private fun computeDistances(level: Level, targets: IntArray, nnf: IntArray, dist: IntArray, step: Int) {
        parallelBands(targets.size) { from, to, _ ->
            for (i in from until to) {
                val t = targets[i]
                dist[t] = distance(level, t, nnf[t], Int.MAX_VALUE, step)
            }
        }
    }

    private fun patchMatchPass(
        level: Level,
        targets: IntArray,
        nnf: IntArray,
        dist: IntArray,
        step: Int,
        reverse: Boolean,
        seed: Int,
    ) {
        val w = level.w
        val h = level.h
        val maxRadius = max(w, h)
        parallelBands(targets.size) { from, to, band ->
            val random = Random(seed * 7919L + band)
            val d = if (reverse) 1 else -1
            var i = if (reverse) to - 1 else from
            while (if (reverse) i >= from else i < to) {
                val t = targets[i]
                var best = nnf[t]
                var bestDist = dist[t]
                val tx = t % w
                val ty = t / w

                // Propagación: el vecino ya procesado sugiere su coincidencia desplazada.
                val nx = tx + d
                if (nx in 0 until w) {
                    val m = nnf[ty * w + nx]
                    if (m >= 0) {
                        val cx = m % w - d
                        if (cx in 0 until w) {
                            val c = (m / w) * w + cx
                            if (level.sourceOk[c] && c != best) {
                                val dc = distance(level, t, c, bestDist, step)
                                if (dc < bestDist) { best = c; bestDist = dc }
                            }
                        }
                    }
                }
                val ny = ty + d
                if (ny in 0 until h) {
                    val m = nnf[ny * w + tx]
                    if (m >= 0) {
                        val cy = m / w - d
                        if (cy in 0 until h) {
                            val c = cy * w + m % w
                            if (level.sourceOk[c] && c != best) {
                                val dc = distance(level, t, c, bestDist, step)
                                if (dc < bestDist) { best = c; bestDist = dc }
                            }
                        }
                    }
                }

                // Búsqueda aleatoria en ventanas cada vez más pequeñas alrededor de la mejor.
                var radius = maxRadius
                val bx = best % w
                val by = best / w
                while (radius >= 1) {
                    val cx = (bx + random.nextInt(2 * radius + 1) - radius).coerceIn(0, w - 1)
                    val cy = (by + random.nextInt(2 * radius + 1) - radius).coerceIn(0, h - 1)
                    val c = cy * w + cx
                    if (level.sourceOk[c] && c != best) {
                        val dc = distance(level, t, c, bestDist, step)
                        if (dc < bestDist) { best = c; bestDist = dc }
                    }
                    radius /= 2
                }

                nnf[t] = best
                dist[t] = bestDist
                i += if (reverse) -1 else 1
            }
        }
    }

    /**
     * Siembra el hueco con la textura fina de la foto: a cada píxel se le suma la diferencia entre
     * un píxel conocido al azar y la media de su entorno. Sin esto, la estimación inicial es lisa
     * y la búsqueda prefiere siempre zonas lisas (sin estrellas, sin grano), que es justo lo que
     * hace que el relleno parezca borroso.
     */
    private fun injectTexture(level: Level, random: Random) {
        val w = level.w
        val h = level.h
        for (q in level.hole.indices) {
            if (!level.hole[q]) continue
            val s = level.randomSource(random)
            val sx = s % w
            val sy = s / w
            var mr = 0
            var mg = 0
            var mb = 0
            var n = 0
            for (yy in max(0, sy - 1)..min(h - 1, sy + 1)) {
                for (xx in max(0, sx - 1)..min(w - 1, sx + 1)) {
                    val p = yy * w + xx
                    mr += level.r[p]; mg += level.g[p]; mb += level.b[p]; n++
                }
            }
            level.r[q] = (level.r[q] + level.r[s] - mr / n).coerceIn(0, 255)
            level.g[q] = (level.g[q] + level.g[s] - mg / n).coerceIn(0, 255)
            level.b[q] = (level.b[q] + level.b[s] - mb / n).coerceIn(0, 255)
        }
    }

    /**
     * Recalcula cada píxel del hueco a partir de los parches que lo cubren: con la media
     * ponderada de todos ellos o, si [bestOnly], con el del parche más parecido, que conserva
     * la textura en lugar de suavizarla.
     */
    private fun vote(level: Level, nnf: IntArray, dist: IntArray, step: Int, bestOnly: Boolean) {
        val w = level.w
        val h = level.h
        val samples = ((2 * RADIUS) / step + 1).let { it * it } * 3
        val holes = IntArray(level.holeCount)
        var k = 0
        for (p in level.hole.indices) if (level.hole[p]) holes[k++] = p
        val newR = IntArray(holes.size)
        val newG = IntArray(holes.size)
        val newB = IntArray(holes.size)
        val twoSigmaSq = 2f * SIGMA * SIGMA

        parallelBands(holes.size) { from, to, _ ->
            for (i in from until to) {
                val q = holes[i]
                val qx = q % w
                val qy = q / w
                var sr = 0f
                var sg = 0f
                var sb = 0f
                var sw = 0f
                var bestDist = Int.MAX_VALUE
                var bestSrc = -1
                for (dy in -RADIUS..RADIUS) {
                    val ty = qy - dy
                    if (ty !in 0 until h) continue
                    for (dx in -RADIUS..RADIUS) {
                        val tx = qx - dx
                        if (tx !in 0 until w) continue
                        val t = ty * w + tx
                        val s = nnf[t]
                        if (s < 0) continue
                        val src = (s / w + dy) * w + (s % w + dx)
                        if (bestOnly) {
                            if (dist[t] < bestDist) {
                                bestDist = dist[t]
                                bestSrc = src
                            }
                            continue
                        }
                        val weight = exp(-(dist[t].toFloat() / samples) / twoSigmaSq) + 1e-4f
                        sr += level.r[src] * weight
                        sg += level.g[src] * weight
                        sb += level.b[src] * weight
                        sw += weight
                    }
                }
                if (bestSrc >= 0) {
                    newR[i] = level.r[bestSrc]; newG[i] = level.g[bestSrc]; newB[i] = level.b[bestSrc]
                } else if (sw > 0f) {
                    newR[i] = (sr / sw).toInt().coerceIn(0, 255)
                    newG[i] = (sg / sw).toInt().coerceIn(0, 255)
                    newB[i] = (sb / sw).toInt().coerceIn(0, 255)
                } else {
                    newR[i] = level.r[q]; newG[i] = level.g[q]; newB[i] = level.b[q]
                }
            }
        }
        for (i in holes.indices) {
            val q = holes[i]
            level.r[q] = newR[i]; level.g[q] = newG[i]; level.b[q] = newB[i]
        }
    }

    // endregion

    /** Reparte [count] elementos en bandas contiguas procesadas en paralelo por todos los núcleos. */
    private inline fun parallelBands(count: Int, crossinline block: (from: Int, to: Int, band: Int) -> Unit) {
        if (count == 0) return
        val bands = min(count, Runtime.getRuntime().availableProcessors() * 2).coerceAtLeast(1)
        val size = (count + bands - 1) / bands
        IntStream.range(0, bands).parallel().forEach { band ->
            val from = band * size
            val to = min(count, from + size)
            if (from < to) block(from, to, band)
        }
    }
}
