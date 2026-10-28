package com.wallpaperfit.app.processing

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import kotlin.math.roundToInt

/**
 * Expande imágenes de prueba hasta la proporción de un móvil (9:20), centradas, con y sin las
 * preferencias de Expandir, y guarda el resultado para revisarlo a ojo.
 * Solo se ejecuta si se define WF_VISUAL_DIR (con WF_VISUAL_NAMES = nombres separados por comas).
 * Formato .rgb: ancho y alto (int) y bytes RGB.
 */
class ExpandVisualTest {

    @Test
    fun expandSamples() {
        val dir = System.getenv("WF_VISUAL_DIR")
        assumeTrue(dir != null)
        val names = (System.getenv("WF_VISUAL_NAMES") ?: "night,clouds").split(",")
        for (name in names) {
            val (inWidth, inHeight, input) = read(File(dir, "$name.rgb"))
            // Lienzo 9:20 que contiene la foto entera.
            val ratio = 9f / 20f
            val width: Int
            val height: Int
            if (inWidth.toFloat() / inHeight > ratio) {
                width = inWidth
                height = (inWidth / ratio).roundToInt()
            } else {
                height = inHeight
                width = (inHeight * ratio).roundToInt()
            }
            val ox = (width - inWidth) / 2
            val oy = (height - inHeight) / 2
            val pixels = IntArray(width * height)
            val hole = BooleanArray(width * height) { true }
            for (y in 0 until inHeight) {
                for (x in 0 until inWidth) {
                    val p = (y + oy) * width + x + ox
                    pixels[p] = input[y * inWidth + x]
                    hole[p] = false
                }
            }
            val prior = Inpainter.ExpandPrior(
                ox.toFloat() / width,
                oy.toFloat() / height,
                (ox + inWidth).toFloat() / width,
                (oy + inHeight).toFloat() / height,
            )
            for ((label, p) in listOf("plain" to null, "prior" to prior)) {
                for (mode in DetailFill.Mode.entries) {
                    val start = System.nanoTime()
                    val filled = DetailFill.fill(pixels, width, height, hole, maxWorkSide = 800, mode = mode, checkCancelled = {}, prior = p)
                    SeamHarmonizer.harmonize(filled, width, height, hole)
                    println("$name $label $mode: ${(System.nanoTime() - start) / 1_000_000} ms")
                    write(File(dir, "${name}_${label}_${mode.name.lowercase()}.rgb"), width, height, filled)
                }
            }
        }
    }

    private fun read(file: File): Triple<Int, Int, IntArray> = DataInputStream(file.inputStream().buffered()).use { input ->
        val width = input.readInt()
        val height = input.readInt()
        val pixels = IntArray(width * height) {
            (0xFF shl 24) or (input.readUnsignedByte() shl 16) or (input.readUnsignedByte() shl 8) or input.readUnsignedByte()
        }
        Triple(width, height, pixels)
    }

    private fun write(file: File, width: Int, height: Int, pixels: IntArray) {
        DataOutputStream(file.outputStream().buffered()).use { out ->
            out.writeInt(width)
            out.writeInt(height)
            for (c in pixels) {
                out.writeByte((c shr 16) and 0xFF)
                out.writeByte((c shr 8) and 0xFF)
                out.writeByte(c and 0xFF)
            }
        }
    }
}
