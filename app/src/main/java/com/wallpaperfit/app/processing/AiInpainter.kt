package com.wallpaperfit.app.processing

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Relleno con IA en el propio móvil: MI-GAN (Picsart AI Research, ICCV 2023, licencia MIT),
 * una red generativa ligera entrenada para borrar objetos y reconstruir lo que hay detrás.
 *
 * El modelo trabaja siempre sobre 512 × 512: aquí se recorta una ventana alrededor de la zona a
 * rellenar, se escala, se ejecuta y se vuelve a pegar. Se ejecuta en la GPU si el móvil la
 * admite; si no, en la CPU.
 *
 * El intérprete no es seguro entre hilos y el delegado GPU debe usarse desde el hilo que lo creó,
 * así que todas las llamadas pasan por un único hilo propio.
 */
class AiInpainter(private val context: Context) : Closeable {

    private val executor = Executors.newSingleThreadExecutor { Thread(it, "ai-inpainter") }
    private var interpreter: Interpreter? = null
    private var gpu: GpuDelegate? = null
    private val input = ByteBuffer.allocateDirect(4 * 4 * SIZE * SIZE).order(ByteOrder.nativeOrder())
    private val output = ByteBuffer.allocateDirect(4 * 3 * SIZE * SIZE).order(ByteOrder.nativeOrder())
    private val inputArray = FloatArray(4 * SIZE * SIZE)
    private val outputArray = FloatArray(3 * SIZE * SIZE)

    /** True si el modelo se ejecuta en la GPU (solo informativo). */
    @Volatile
    var usesGpu: Boolean = false
        private set

    /**
     * Rellena los píxeles marcados en [hole] de una imagen de [SIZE] × [SIZE] y devuelve la imagen
     * completa (los píxeles conocidos no cambian).
     */
    fun inpaint512(pixels: IntArray, hole: BooleanArray): IntArray {
        require(pixels.size == SIZE * SIZE && hole.size == SIZE * SIZE)
        return runOnWorker {
            val model = interpreter ?: createInterpreter().also { interpreter = it }
            val started = System.nanoTime()
            val plane = SIZE * SIZE
            // Se rellena un FloatArray y se copia de una vez: escribir el búfer valor a valor es lento.
            for (i in 0 until plane) {
                if (hole[i]) {
                    inputArray[i] = -0.5f
                    inputArray[plane + i] = 0f
                    inputArray[2 * plane + i] = 0f
                    inputArray[3 * plane + i] = 0f
                } else {
                    val c = pixels[i]
                    inputArray[i] = 0.5f
                    inputArray[plane + i] = ((c shr 16) and 0xFF) / 127.5f - 1f
                    inputArray[2 * plane + i] = ((c shr 8) and 0xFF) / 127.5f - 1f
                    inputArray[3 * plane + i] = (c and 0xFF) / 127.5f - 1f
                }
            }
            input.rewind()
            input.asFloatBuffer().put(inputArray)
            output.rewind()
            model.run(input, output)
            // El intérprete deja la posición del búfer al final: se vuelve al principio para leer.
            output.rewind()
            output.asFloatBuffer().get(outputArray)
            Log.i(TAG, "Ventana IA en ${(System.nanoTime() - started) / 1_000_000} ms (GPU: $usesGpu)")
            IntArray(plane) { i ->
                if (!hole[i]) {
                    pixels[i]
                } else {
                    val r = toByte(outputArray[i])
                    val g = toByte(outputArray[plane + i])
                    val b = toByte(outputArray[2 * plane + i])
                    (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
        }
    }

    /** Prepara el modelo en segundo plano (la GPU tarda unos segundos la primera vez). */
    fun warmUp() {
        executor.execute {
            try {
                if (interpreter == null) interpreter = createInterpreter()
            } catch (e: Throwable) {
                Log.w(TAG, "No se pudo preparar el modelo de IA", e)
            }
        }
    }

    private fun toByte(v: Float): Int = ((v + 1f) * 127.5f).roundToInt().coerceIn(0, 255)

    private fun createInterpreter(): Interpreter {
        val model = loadModel()
        try {
            val delegate = GpuDelegate()
            val created = Interpreter(model, Interpreter.Options().addDelegate(delegate))
            gpu = delegate
            usesGpu = true
            return created
        } catch (e: Throwable) {
            Log.w(TAG, "GPU no disponible para la IA; se usa la CPU", e)
            gpu?.close()
            gpu = null
        }
        // XNNPACK no admite las convoluciones fp16 de este modelo: se usan los núcleos normales.
        val options = Interpreter.Options()
            .setNumThreads(max(1, min(4, Runtime.getRuntime().availableProcessors())))
            .setUseXNNPACK(false)
        usesGpu = false
        return Interpreter(model, options)
    }

    private fun loadModel(): MappedByteBuffer {
        val descriptor = context.assets.openFd(MODEL_ASSET)
        FileInputStream(descriptor.fileDescriptor).use { stream ->
            return stream.channel.map(FileChannel.MapMode.READ_ONLY, descriptor.startOffset, descriptor.declaredLength)
        }
    }

    private fun <T> runOnWorker(block: () -> T): T = try {
        executor.submit(block).get()
    } catch (e: ExecutionException) {
        throw e.cause ?: e
    }

    override fun close() {
        executor.submit {
            interpreter?.close()
            interpreter = null
            gpu?.close()
            gpu = null
        }
        executor.shutdown()
    }

    companion object {
        /** Lado de la imagen que acepta el modelo. */
        const val SIZE = 512
        private const val MODEL_ASSET = "migan.tflite"
        private const val TAG = "AiInpainter"
    }
}
