package com.wallpaperfit.app.processing

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Relleno "con textura" de Expandir. No copia trozos de la foto, que repetidos se ven como rayas
 * en una dirección: inventa una textura nueva con la misma estadística que la zona de la foto
 * junto a cada lado, escala a escala (síntesis de Heeger y Bergen). Nubes, nebulosas, degradados
 * y grano quedan sin repeticiones ni dirección preferente.
 *
 * - El color general sale del borde de la foto, estirado hacia fuera y cada vez más difuminado:
 *   continúa los degradados y funde lo que haya en el borde, sin copiar objetos.
 * - El detalle sale de ruido al que se le da, en cada banda de la pirámide, el histograma de esa
 *   banda en las zonas tranquilas de la foto (los objetos no son textura).
 * - Las estrellas y motas, que el histograma no puede reproducir (son pocas y muy brillantes), se
 *   recortan de la foto y se reparten al azar con su misma densidad.
 */
internal object TextureSynth {

    /** Rectángulo en píxeles: [right] y [bottom] no incluidos. */
    data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width get() = right - left
        val height get() = bottom - top
    }

    private class Plane(val data: FloatArray, val width: Int, val height: Int)

    /** Partícula recortada: lo que se suma a cada canal (lado [STAMP_SIDE]). */
    private class Stamp(val y: FloatArray, val cb: FloatArray, val cr: FloatArray)

    /**
     * [pixels]: el lienzo con la foto puesta en [photo]; lo que hay en [hole] no se mira. El relleno
     * entra también [feather] píxeles bajo la foto, en los lados expandidos: es sobre lo que luego
     * se funde su borde (si debajo siguiera la propia foto, la unión se vería igual).
     */
    fun fill(pixels: IntArray, width: Int, height: Int, hole: BooleanArray, photo: Box, feather: Int, checkCancelled: () -> Unit): IntArray {
        val reach = Reach(photo, feather)
        val y = FloatArray(pixels.size)
        val cb = FloatArray(pixels.size)
        val cr = FloatArray(pixels.size)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16 and 0xFF).toFloat()
            val g = (p shr 8 and 0xFF).toFloat()
            val b = (p and 0xFF).toFloat()
            y[i] = 0.299f * r + 0.587f * g + 0.114f * b
            cb[i] = b - y[i]
            cr[i] = r - y[i]
        }
        val levels = (log2(min(photo.width, photo.height) * SOURCE_DEPTH / MIN_BAND_SIDE).toInt()).coerceIn(2, MAX_LEVELS)
        val lowY = stretchEdges(y, width, height, photo, reach)
        val lowCb = stretchEdges(cb, width, height, photo, reach)
        val lowCr = stretchEdges(cr, width, height, photo, reach)
        checkCancelled()

        val detailY = FloatArray(pixels.size)
        val detailCb = FloatArray(pixels.size)
        val detailCr = FloatArray(pixels.size)
        // Semilla fija: la vista previa y el resultado salen parecidos, y deshacer no cambia nada.
        val random = Random(SEED)
        val regions = listOf(
            Box(0, 0, if (photo.left > 0) min(photo.left + feather, width) else 0, height),
            Box(if (photo.right < width) max(photo.right - feather, 0) else width, 0, width, height),
            Box(0, 0, width, if (photo.top > 0) min(photo.top + feather, height) else 0),
            Box(0, if (photo.bottom < height) max(photo.bottom - feather, 0) else height, width, height),
        )
        if (photo.width >= MIN_DEPTH && photo.height >= MIN_DEPTH) {
            // Un solo reducido para todo: lo que cabe en un móvil, y las escalas coinciden.
            val area = max(photo.width * photo.height, regions.maxOf { it.width * it.height })
            val factor = ceil(sqrt(area / MAX_SYNTH_PIXELS)).toInt().coerceAtLeast(1)
            val srcY = extract(y, width, photo, factor)
            val srcCb = extract(cb, width, photo, factor)
            val srcCr = extract(cr, width, photo, factor)
            val stamps = cutStamps(srcY, srcCb, srcCr, random)
            checkCancelled()
            val stats = Stats(srcY, srcCb, srcCr, levels)
            for ((index, region) in regions.withIndex()) {
                if (region.width <= 0 || region.height <= 0) continue
                checkCancelled()
                val horizontal = index < 2
                val outW = max(ceil(region.width.toFloat() / factor).toInt(), MIN_DEPTH)
                val outH = max(ceil(region.height.toFloat() / factor).toInt(), MIN_DEPTH)
                val (synthY, synthCb, synthCr) = synthesize(stats, outW, outH, levels, random, checkCancelled)
                scatter(stamps, srcY.width * srcY.height, synthY, synthCb, synthCr, outW, outH, random)
                accumulate(detailY, synthY, outW, region, reach, horizontal, width, factor)
                accumulate(detailCb, synthCb, outW, region, reach, horizontal, width, factor)
                accumulate(detailCr, synthCr, outW, region, reach, horizontal, width, factor)
            }
        }
        checkCancelled()

        val out = pixels.copyOf()
        val dither = Random(SEED + 1)
        for (i in out.indices) {
            if (!hole[i] && reach.x(i % width, width) == 0 && reach.y(i / width, height) == 0) continue
            // Un poco de ruido al pasar a 8 bits: los degradados no forman escalones.
            val noise = dither.nextFloat() - 0.5f
            val l = lowY[i] + detailY[i] + noise
            val r = l + lowCr[i] + detailCr[i]
            val b = l + lowCb[i] + detailCb[i]
            val g = (l - 0.299f * r - 0.114f * b) / 0.587f
            out[i] = (0xFF shl 24) or (to8(r) shl 16) or (to8(g) shl 8) or to8(b)
        }
        return out
    }

    private fun to8(value: Float): Int = value.roundToInt().coerceIn(0, 255)

    /**
     * A cuánto está un punto de la foto, por cada eje; 0 si no le toca relleno. Dentro de la franja
     * que se funde cuenta como 1: el color del borde sin alejarse.
     */
    private class Reach(private val photo: Box, private val feather: Int) {
        private fun distance(position: Int, low: Int, high: Int, size: Int): Int = when {
            position < low -> low - position
            position >= high -> position - high + 1
            low > 0 && position - low < feather -> 1
            high < size && high - 1 - position < feather -> 1
            else -> 0
        }

        fun x(position: Int, width: Int) = distance(position, photo.left, photo.right, width)

        fun y(position: Int, height: Int) = distance(position, photo.top, photo.bottom, height)
    }

    // region Color general

    /** Media de cada posición a lo largo de un borde de la foto, con sumas acumuladas para difuminar sin coste. */
    private class Edge(values: FloatArray) {
        private val sums = FloatArray(values.size + 1).also { for (i in values.indices) it[i + 1] = it[i] + values[i] }
        private val last = values.size - 1

        private fun mean(center: Int, radius: Int): Float {
            val from = (center - radius).coerceIn(0, last)
            val to = (center + radius).coerceIn(0, last)
            return (sums[to + 1] - sums[from]) / (to - from + 1)
        }

        /** Dos anchos a la vez: el difuminado no deja escalones visibles. */
        fun at(position: Int, radius: Int): Float {
            val center = position.coerceIn(0, last)
            return (mean(center, radius) + mean(center, radius / 2)) / 2f
        }
    }

    private fun stretchEdges(plane: FloatArray, width: Int, height: Int, photo: Box, reach: Reach): FloatArray {
        val band = max(MIN_EDGE_BAND, (min(photo.width, photo.height) * EDGE_BAND).roundToInt())
        val start = (min(photo.width, photo.height) * EDGE_START).toInt()
        fun rows(from: Int, to: Int) = Edge(
            FloatArray(photo.height) { row ->
                var sum = 0f
                for (x in from until to) sum += plane[(photo.top + row) * width + x]
                sum / (to - from)
            },
        )
        fun columns(from: Int, to: Int) = Edge(
            FloatArray(photo.width) { column ->
                var sum = 0f
                for (row in from until to) sum += plane[row * width + photo.left + column]
                sum / (to - from)
            },
        )
        val left = rows(photo.left, min(photo.left + band, photo.right))
        val right = rows(max(photo.right - band, photo.left), photo.right)
        val top = columns(photo.top, min(photo.top + band, photo.bottom))
        val bottom = columns(max(photo.bottom - band, photo.top), photo.bottom)
        val out = FloatArray(plane.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val dx = reach.x(x, width)
                val dy = reach.y(y, height)
                if (dx == 0 && dy == 0) continue
                val p = y * width + x
                val row = y - photo.top
                val column = x - photo.left
                // Dentro de la foto (franja que se funde), el borde más cercano.
                val useLeft = x < photo.left || (x < photo.right && x - photo.left < photo.right - 1 - x)
                val useTop = y < photo.top || (y < photo.bottom && y - photo.top < photo.bottom - 1 - y)
                val fromSides = (if (useLeft) left else right).at(row, start + (dx * EDGE_SPREAD).toInt())
                val fromEnds = (if (useTop) top else bottom).at(column, start + (dy * EDGE_SPREAD).toInt())
                out[p] = when {
                    dy == 0 -> fromSides
                    dx == 0 -> fromEnds
                    else -> (fromSides * dx + fromEnds * dy) / (dx + dy)
                }
            }
        }
        return out
    }

    // endregion

    // region Síntesis

    /** Lo que se mide de la foto, banda a banda. */
    private class Stats(y: Plane, cb: Plane, cr: Plane, levels: Int) {
        private val bandsY = laplacian(y, levels)
        val quantilesY = bandsY.mapIndexed { level, band -> quantiles(band.data, band.width, level >= CLIP_FROM_LEVEL) }

        // El color acompaña al brillo en cada banda, y lo que queda se sintetiza aparte.
        private val bandsCb = laplacian(cb, levels)
        private val bandsCr = laplacian(cr, levels)
        val gainCb = FloatArray(levels) { regression(bandsY[it].data, bandsCb[it].data) }
        val gainCr = FloatArray(levels) { regression(bandsY[it].data, bandsCr[it].data) }
        val restCb = bandsCb.mapIndexed { level, band -> quantiles(residual(band.data, bandsY[level].data, gainCb[level]), band.width, level >= CLIP_FROM_LEVEL) }
        val restCr = bandsCr.mapIndexed { level, band -> quantiles(residual(band.data, bandsY[level].data, gainCr[level]), band.width, level >= CLIP_FROM_LEVEL) }
    }

    private fun synthesize(
        stats: Stats,
        width: Int,
        height: Int,
        levels: Int,
        random: Random,
        checkCancelled: () -> Unit,
    ): Triple<FloatArray, FloatArray, FloatArray> {
        var current = Plane(FloatArray(width * height) { random.nextFloat() - 0.5f }, width, height)
        var result = laplacian(current, levels)
        repeat(ITERATIONS) {
            checkCancelled()
            result = laplacian(current, levels)
            result.forEachIndexed { level, band -> matchHistogram(band.data, stats.quantilesY[level]) }
            current = collapse(result, width, height)
        }
        result = laplacian(current, levels)
        result.forEachIndexed { level, band -> matchHistogram(band.data, stats.quantilesY[level]) }

        fun chroma(rest: List<FloatArray>, gain: FloatArray): FloatArray {
            val noise = laplacian(Plane(FloatArray(width * height) { random.nextFloat() - 0.5f }, width, height), levels)
            noise.forEachIndexed { level, band ->
                matchHistogram(band.data, rest[level])
                val luma = result[level].data
                for (i in band.data.indices) band.data[i] += gain[level] * luma[i]
            }
            return collapse(noise, width, height).data
        }
        return Triple(collapse(result, width, height).data, chroma(stats.restCb, stats.gainCb), chroma(stats.restCr, stats.gainCr))
    }

    private fun regression(x: FloatArray, y: FloatArray): Float {
        var xy = 0f
        var xx = 0f
        for (i in x.indices) {
            xy += x[i] * y[i]
            xx += x[i] * x[i]
        }
        return if (xx > 0f) xy / xx else 0f
    }

    private fun residual(values: FloatArray, luma: FloatArray, gain: Float) = FloatArray(values.size) { values[it] - gain * luma[it] }

    /**
     * Valores de una banda repartidos en cuantiles, solo de sus trozos más tranquilos: lo más
     * movido son bordes de objetos, y como textura salen manchas. Con [clip] (bandas gruesas) solo
     * se toma su fuerza y se reparte como una campana: una distribución con pocos valores muy
     * altos, propia de un cielo casi liso, daría borrones sueltos en vez de nubes suaves.
     */
    private fun quantiles(values: FloatArray, width: Int, clip: Boolean): FloatArray {
        val height = values.size / width
        val side = min(QUIET_BLOCK, min(width, height))
        val columns = width / side
        val rows = height / side
        val energy = FloatArray(columns * rows) { block ->
            var sum = 0f
            val top = (block / columns) * side
            val left = (block % columns) * side
            for (y in top until top + side) for (x in left until left + side) sum += values[y * width + x] * values[y * width + x]
            sum
        }
        val limit = energy.sortedArray()[(energy.size * QUIET_SHARE).toInt().coerceAtMost(energy.size - 1)]
        val quiet = ArrayList<Float>()
        for (block in energy.indices) {
            if (energy[block] > limit) continue
            val top = (block / columns) * side
            val left = (block % columns) * side
            for (y in top until top + side) for (x in left until left + side) quiet += values[y * width + x]
        }
        val stride = max(1, quiet.size / MAX_QUANTILE_SAMPLES)
        val sample = FloatArray(quiet.size / stride) { quiet[it * stride] }
        sample.sort()
        if (clip) {
            val from = (sample.size * CLIP_FRACTION).toInt()
            val to = sample.size - 1 - from
            var sum = 0f
            for (i in from..to) sum += sample[i] * sample[i]
            val rms = sqrt(sum / (to - from + 1))
            return FloatArray(QUANTILES) { rms * normalQuantile((it + 0.5f) / QUANTILES) }
        }
        return FloatArray(QUANTILES) { sample[(sample.size - 1) * it / (QUANTILES - 1)] }
    }

    /** Valor de una normal estándar que deja por debajo la fracción [p] (aproximación de Winitzki). */
    private fun normalQuantile(p: Float): Float {
        val x = 2f * p - 1f
        val a = 0.147f
        val log = ln(1f - x * x)
        val term = 2f / (PI.toFloat() * a) + log / 2f
        val root = sqrt(sqrt(term * term - log / a) - term)
        return sqrt(2f) * if (x < 0f) -root else root
    }

    /** Da a [values] la distribución de [target], manteniendo el orden de los valores. */
    private fun matchHistogram(values: FloatArray, target: FloatArray) {
        var low = Float.POSITIVE_INFINITY
        var high = Float.NEGATIVE_INFINITY
        for (v in values) {
            if (v < low) low = v
            if (v > high) high = v
        }
        val range = high - low
        if (range <= 0f) return
        val scale = (BINS - 1) / range
        val counts = IntArray(BINS)
        for (v in values) counts[((v - low) * scale).toInt()]++
        val map = FloatArray(BINS)
        var seen = 0L
        for (bin in 0 until BINS) {
            // El centro del grupo de valores de este tramo.
            val rank = (seen + counts[bin] / 2f) / values.size * (target.size - 1)
            val index = min(rank.toInt(), target.size - 2)
            val t = rank - index
            map[bin] = target[index] + (target[index + 1] - target[index]) * t
            seen += counts[bin]
        }
        for (i in values.indices) values[i] = map[((values[i] - low) * scale).toInt()]
    }

    // endregion

    // region Pirámide

    /** Filtro 1-2-1 en las dos direcciones: sin él, el ruido ampliado forma rombos y burbujas. */
    private fun soften(values: FloatArray, width: Int, height: Int): FloatArray {
        val rows = FloatArray(values.size)
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                rows[row + x] = (values[row + max(x - 1, 0)] + 2 * values[row + x] + values[row + min(x + 1, width - 1)]) / 4f
            }
        }
        val out = FloatArray(values.size)
        for (y in 0 until height) {
            val above = max(y - 1, 0) * width
            val row = y * width
            val below = min(y + 1, height - 1) * width
            for (x in 0 until width) out[row + x] = (rows[above + x] + 2 * rows[row + x] + rows[below + x]) / 4f
        }
        return out
    }

    private fun downsample(plane: Plane): Plane {
        val w = max(1, plane.width / 2)
        val h = max(1, plane.height / 2)
        val out = FloatArray(w * h)
        val d = soften(plane.data, plane.width, plane.height)
        for (y in 0 until h) {
            val y0 = min(y * 2, plane.height - 1)
            val y1 = min(y0 + 1, plane.height - 1)
            for (x in 0 until w) {
                val x0 = min(x * 2, plane.width - 1)
                val x1 = min(x0 + 1, plane.width - 1)
                out[y * w + x] = (d[y0 * plane.width + x0] + d[y0 * plane.width + x1] + d[y1 * plane.width + x0] + d[y1 * plane.width + x1]) / 4f
            }
        }
        return Plane(out, w, h)
    }

    private fun upsample(plane: Plane, width: Int, height: Int): FloatArray {
        val out = FloatArray(width * height)
        val d = plane.data
        val sx = plane.width.toFloat() / width
        val sy = plane.height.toFloat() / height
        for (y in 0 until height) {
            val fy = ((y + 0.5f) * sy - 0.5f).coerceIn(0f, plane.height - 1f)
            val y0 = floor(fy).toInt()
            val y1 = min(y0 + 1, plane.height - 1)
            val ty = fy - y0
            for (x in 0 until width) {
                val fx = ((x + 0.5f) * sx - 0.5f).coerceIn(0f, plane.width - 1f)
                val x0 = floor(fx).toInt()
                val x1 = min(x0 + 1, plane.width - 1)
                val tx = fx - x0
                val top = d[y0 * plane.width + x0] * (1 - tx) + d[y0 * plane.width + x1] * tx
                val bottom = d[y1 * plane.width + x0] * (1 - tx) + d[y1 * plane.width + x1] * tx
                out[y * width + x] = top * (1 - ty) + bottom * ty
            }
        }
        return soften(out, width, height)
    }

    /** Las bandas de detalle, de la más fina a la más gruesa. Lo que queda por debajo se descarta. */
    private fun laplacian(plane: Plane, levels: Int): List<Plane> {
        val bands = ArrayList<Plane>(levels)
        var current = plane
        repeat(levels) {
            val next = downsample(current)
            val up = upsample(next, current.width, current.height)
            bands += Plane(FloatArray(up.size) { current.data[it] - up[it] }, current.width, current.height)
            current = next
        }
        return bands
    }

    private fun collapse(bands: List<Plane>, width: Int, height: Int): Plane {
        var current = FloatArray(bands.last().data.size)
        for (level in bands.indices.reversed()) {
            val band = bands[level]
            if (level < bands.lastIndex) {
                val previous = bands[level + 1]
                current = upsample(Plane(current, previous.width, previous.height), band.width, band.height)
            }
            for (i in current.indices) current[i] += band.data[i]
        }
        return Plane(current, width, height)
    }

    /** Lo que la pirámide no sintetiza: el color general, con la misma resolución que [plane]. */
    private fun lowPass(plane: Plane, levels: Int): FloatArray {
        var current = plane
        repeat(levels) { current = downsample(current) }
        return upsample(current, plane.width, plane.height)
    }

    // endregion

    // region Estrellas y motas

    /** Recorta de [y], [cb] y [cr] los puntos pequeños y brillantes. Si son demasiados, son textura y se quedan. */
    private fun cutStamps(y: Plane, cb: Plane, cr: Plane, random: Random): List<Stamp> {
        val w = y.width
        val h = y.height
        if (w <= STAMP_SIDE * 2 || h <= STAMP_SIDE * 2) return emptyList()
        val localY = lowPass(y, 3)
        val localCb = lowPass(cb, 3)
        val localCr = lowPass(cr, 3)
        val high = FloatArray(w * h) { y.data[it] - localY[it] }
        val sample = FloatArray(high.size / max(1, high.size / MAX_QUANTILE_SAMPLES)) { kotlin.math.abs(high[it * max(1, high.size / MAX_QUANTILE_SAMPLES)]) }
        sample.sort()
        val threshold = max(MIN_STAR_CONTRAST, sample[sample.size / 2] * STAR_SIGMAS)
        val radius = STAMP_SIDE / 2
        val peaks = ArrayList<Int>()
        for (py in radius until h - radius) {
            for (px in radius until w - radius) {
                val p = py * w + px
                val value = high[p]
                if (value < threshold) continue
                var peak = true
                for (dy in -2..2) {
                    for (dx in -2..2) if (high[p + dy * w + dx] > value) peak = false
                }
                if (!peak) continue
                // Compacto: a la distancia del borde del recorte ya no queda brillo (si no, es un objeto).
                var ring = 0f
                for (k in -radius..radius) {
                    ring = max(ring, high[(py - radius) * w + px + k])
                    ring = max(ring, high[(py + radius) * w + px + k])
                    ring = max(ring, high[(py + k) * w + px - radius])
                    ring = max(ring, high[(py + k) * w + px + radius])
                }
                if (ring < value * STAR_RING) peaks += p
            }
        }
        if (peaks.isEmpty() || peaks.size > w * h * MAX_STAR_DENSITY) return emptyList()
        val stamps = ArrayList<Stamp>(peaks.size)
        for (p in peaks) {
            val py = p / w
            val px = p % w
            val stampY = FloatArray(STAMP_SIDE * STAMP_SIDE)
            val stampCb = FloatArray(stampY.size)
            val stampCr = FloatArray(stampY.size)
            for (dy in -radius..radius) {
                for (dx in -radius..radius) {
                    val q = (py + dy) * w + px + dx
                    val s = (dy + radius) * STAMP_SIDE + dx + radius
                    // Se apaga hacia el borde para que no se note el cuadrado.
                    val fade = (1f - sqrt((dx * dx + dy * dy).toFloat()) / (radius + 1)).coerceAtLeast(0f)
                    stampY[s] = (y.data[q] - localY[q]) * fade
                    stampCb[s] = (cb.data[q] - localCb[q]) * fade
                    stampCr[s] = (cr.data[q] - localCr[q]) * fade
                    y.data[q] -= stampY[s]
                    cb.data[q] -= stampCb[s]
                    cr.data[q] -= stampCr[s]
                }
            }
            stamps += Stamp(stampY, stampCb, stampCr)
        }
        stamps.shuffle(random)
        return stamps
    }

    private fun scatter(stamps: List<Stamp>, sourceArea: Int, y: FloatArray, cb: FloatArray, cr: FloatArray, width: Int, height: Int, random: Random) {
        if (stamps.isEmpty() || width <= STAMP_SIDE || height <= STAMP_SIDE) return
        val count = (stamps.size.toFloat() / sourceArea * width * height).roundToInt()
        repeat(count) {
            val stamp = stamps[random.nextInt(stamps.size)]
            val left = random.nextInt(width - STAMP_SIDE)
            val top = random.nextInt(height - STAMP_SIDE)
            for (row in 0 until STAMP_SIDE) {
                for (column in 0 until STAMP_SIDE) {
                    val q = (top + row) * width + left + column
                    val s = row * STAMP_SIDE + column
                    y[q] += stamp.y[s]
                    cb[q] += stamp.cb[s]
                    cr[q] += stamp.cr[s]
                }
            }
        }
    }

    // endregion

    private fun extract(values: FloatArray, stride: Int, box: Box, factor: Int): Plane {
        val w = max(1, box.width / factor)
        val h = max(1, box.height / factor)
        val out = FloatArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var sum = 0f
                for (dy in 0 until factor) {
                    for (dx in 0 until factor) sum += values[(box.top + y * factor + dy) * stride + box.left + x * factor + dx]
                }
                out[y * w + x] = sum / (factor * factor)
            }
        }
        return Plane(out, w, h)
    }

    /**
     * Suma el detalle de un lado. En las esquinas se reparte entre los dos lados que se tocan, según
     * cuánto se aleja de cada uno, y se compensa para que el detalle no pierda fuerza al mezclarse.
     */
    private fun accumulate(
        out: FloatArray,
        synth: FloatArray,
        synthWidth: Int,
        region: Box,
        reach: Reach,
        horizontal: Boolean,
        width: Int,
        factor: Int,
    ) {
        val height = out.size / width
        for (y in region.top until region.bottom) {
            for (x in region.left until region.right) {
                val dx = reach.x(x, width).toFloat()
                val dy = reach.y(y, height).toFloat()
                val total = dx + dy
                if (total <= 0f) continue
                val wx = dx / total
                val wy = dy / total
                val weight = (if (horizontal) wx else wy) / sqrt(wx * wx + wy * wy)
                if (weight <= 0f) continue
                val sx = (x - region.left) / factor
                val sy = (y - region.top) / factor
                out[y * width + x] += synth[sy * synthWidth + sx] * weight
            }
        }
    }

    private const val SEED = 20240817

    /** Parte de la foto que marca la mayor escala de la textura. */
    private const val SOURCE_DEPTH = 0.3f
    private const val MIN_DEPTH = 32
    private const val MIN_BAND_SIDE = 8f
    private const val MAX_LEVELS = 6
    private const val ITERATIONS = 2

    /** Más píxeles que esto en un lado se sintetizan a menos resolución: es lo que cabe en un móvil. */
    private const val MAX_SYNTH_PIXELS = 2_500_000f
    private const val BINS = 2048
    private const val QUANTILES = 512
    private const val MAX_QUANTILE_SAMPLES = 200_000

    /** En las bandas gruesas, los extremos son bordes de objetos, no textura. */
    private const val CLIP_FROM_LEVEL = 1
    private const val CLIP_FRACTION = 0.04f

    /** Trozos para buscar las zonas tranquilas de cada banda, y qué parte de ellos cuenta. */
    private const val QUIET_BLOCK = 16
    private const val QUIET_SHARE = 0.35f

    /** Parte de la foto que da el color de cada borde, y cuánto se difumina por píxel que se aleja. */
    private const val EDGE_BAND = 0.02f
    private const val MIN_EDGE_BAND = 2
    private const val EDGE_SPREAD = 1f
    private const val EDGE_START = 0.025f

    private const val STAMP_SIDE = 11
    private const val MIN_STAR_CONTRAST = 14f
    private const val STAR_SIGMAS = 6f
    private const val STAR_RING = 0.3f
    private const val MAX_STAR_DENSITY = 0.004f
}
