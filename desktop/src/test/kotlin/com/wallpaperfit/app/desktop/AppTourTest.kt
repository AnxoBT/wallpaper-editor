package com.wallpaperfit.app.desktop

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.wallpaperfit.app.model.FillDetail
import com.wallpaperfit.app.model.HealEngine
import com.wallpaperfit.app.model.HealStroke
import com.wallpaperfit.app.model.SaveMode
import com.wallpaperfit.app.model.Tool
import com.wallpaperfit.app.storage.PhotoTags
import com.wallpaperfit.app.storage.WallpaperSetter
import com.wallpaperfit.app.model.WallpaperTarget
import com.wallpaperfit.app.wallpaper.ImageCatalog
import com.wallpaperfit.app.wallpaper.WallpaperSource
import com.wallpaperfit.app.wallpaper.WallpaperStore
import com.wallpaperfit.app.R
import com.wallpaperfit.app.ui.EditorViewModel
import com.wallpaperfit.app.ui.WallpaperFitApp
import com.wallpaperfit.app.ui.theme.WallpaperFitTheme
import org.jetbrains.skia.Image
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Recorre la app de verdad, sin ventana y con fotos generadas: selector, todas las herramientas,
 * guardar una copia y reemplazar la original. Deja capturas en build/tour para poder mirarlas.
 * Nunca toca el fondo de Windows ni los datos reales (ver [AppFiles]).
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class AppTourTest {

    private val shots = File("build/tour").apply {
        deleteRecursively()
        mkdirs()
    }

    private fun photo(file: File, width: Int, height: Int, tint: Int) {
        val pixels = IntArray(width * height) { i ->
            val x = i % width
            val y = i / width
            val r = 255 * x / width
            val g = 255 * y / height
            val b = (tint + (x / 40 + y / 40) % 2 * 60) and 0xFF
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        file.parentFile.mkdirs()
        file.writeBytes(ImageEncoder.encode(bitmap, Bitmap.CompressFormat.JPEG, 90))
        bitmap.recycle()
    }

    private fun sizeOf(file: File): Pair<Int, Int> = Image.makeFromEncoded(file.readBytes()).use { it.width to it.height }

    @Test
    fun recorreLaApp() {
        val pictures = File(root, "fotos")
        val original = File(pictures, "pins/7b0814ae7b1130ac5f4d9525edcb52d5.jpg")
        photo(original, 1080, 2400, 40)
        repeat(12) { photo(File(pictures, "pins/foto$it.jpg"), 540, 1200, it * 20) }
        photo(File(pictures, "otra/paisaje.png"), 1600, 900, 200)
        PhotoLibrary.addFolder(pictures)
        Strings.language = "es"

        val app = Activity()
        val viewModel = EditorViewModel(app)
        val photos = PhotoLibrary.photos()
        assertEquals(14, photos.size)

        // La ventana real ya trae uno; sin ventana hay que ponerlo.
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        }

        runDesktopComposeUiTest(1280, 860) {
            setContent {
                CompositionLocalProvider(LocalContext provides app, LocalLifecycleOwner provides owner) {
                    WallpaperFitTheme { WallpaperFitApp(viewModel = viewModel) }
                }
            }
            fun shot(name: String) {
                waitForIdle()
                javax.imageio.ImageIO.write(onAllNodes(isRoot()).onLast().captureToImage().toAwtImage(), "png", File(shots, "$name.png"))
            }
            shot("01_inicio")
            onNode(hasText(Strings.get(R.string.hub_edit))).performClick()
            waitUntil(timeoutMillis = 20_000L) { onAllNodes(hasText(Strings.get(R.string.picker_all))).fetchSemanticsNodes().isNotEmpty() }
            Thread.sleep(1500)
            shot("02_selector")

            onNode(hasContentDescription(Strings.get(R.string.action_back))).performClick()
            shot("03_carpetas")
            onNode(hasContentDescription(Strings.get(R.string.action_back))).performClick()
            shot("04_inicio_otra_vez")

            // Etiquetas: el mismo JSON que exporta el móvil, y Fondos con una carpeta y una etiqueta.
            val tags = PhotoTags.get(app)
            tags.import("""{"foto1.jpg":["ghibli","anime"],"foto2.jpg":["anime"],"#created":["vacia"]}""".byteInputStream())
            assertEquals(2, tags.withTag("anime").size)
            assertEquals(2, tags.queryNamed(tags.withTag("anime")).size)
            val store = WallpaperStore.get(app)
            store.updateConfig {
                it.copy(
                    sources = listOf(
                        WallpaperSource(Uri.fromFile(File(pictures, "pins")).toString(), true, "pins"),
                        WallpaperSource.forTag("anime"),
                    ),
                )
            }
            onNode(hasText(Strings.get(R.string.hub_wallpapers))).performClick()
            Thread.sleep(800)
            shot("04b_fondos")
            onNode(hasText(Strings.get(R.string.wp_show_all, 2))).performClick()
            Thread.sleep(500)
            shot("04b_ver_filtradas")
            onNode(hasText(Strings.get(R.string.wp_all_only_filter, "anime", 2))).performClick()
            waitForIdle()
            Thread.sleep(500)
            onNode(hasText(Strings.get(R.string.wp_all_title, 13))).assertExists()
            shot("04b_ver_todas")
            onNode(hasText(Strings.get(R.string.action_back))).performClick()
            waitForIdle()
            // Era solo para ver: el pase sigue filtrado.
            assertEquals(2, kotlinx.coroutines.runBlocking { ImageCatalog.images(app, store.config.value.sources) }.size)
            // Con dos etiquetas, solo las fotos que tienen las dos.
            val both = store.config.value.sources + WallpaperSource.forTag("ghibli")
            assertEquals(listOf("foto1.jpg"), kotlinx.coroutines.runBlocking { ImageCatalog.images(app, both) }.map { Uri.parse(it).toFile()!!.name })
            onNode(hasContentDescription(Strings.get(R.string.action_back))).performClick()
            waitForIdle()
            // La etiqueta filtra: de las 13 fotos de la carpeta, solo las 2 que la tienen.
            assertEquals(2, kotlinx.coroutines.runBlocking { ImageCatalog.images(app, store.config.value.sources) }.size)
            onNode(hasContentDescription(Strings.get(R.string.settings_open))).performClick()
            Thread.sleep(300)
            shot("04c_ajustes")
            onNode(hasContentDescription(Strings.get(R.string.action_back))).performClick()
            waitForIdle()

            runOnUiThread { viewModel.loadImage(Uri.fromFile(original)) }
            waitUntil(timeoutMillis = 30_000L) { viewModel.uiState.value.preview != null && !viewModel.uiState.value.isProcessing }
            shot("05_editor")

            // Todas las herramientas: abrir (con su efecto inicial) y confirmar.
            Tool.entries.forEach { tool ->
                runOnUiThread { viewModel.openTool(tool) }
                Thread.sleep(700)
                waitUntil(timeoutMillis = 30_000L) { !viewModel.uiState.value.isProcessing }
                shot("tool_${tool.name}")
                runOnUiThread { viewModel.applyTool() }
                waitUntil(timeoutMillis = 30_000L) { !viewModel.uiState.value.isProcessing }
            }

            // Expandir con textura hasta la pantalla.
            runOnUiThread {
                viewModel.openTool(Tool.EXPAND)
                viewModel.onExpandDetailSelected(FillDetail.TEXTURE)
                viewModel.fitExpansionToScreen()
            }
            Thread.sleep(800)
            waitUntil(timeoutMillis = 60_000L) { !viewModel.uiState.value.isExpandDraftLoading }
            shot("05b_expandir_textura")
            runOnUiThread { viewModel.applyTool() }
            Thread.sleep(500)
            waitUntil(timeoutMillis = 120_000L) { !viewModel.uiState.value.isProcessing }
            shot("05c_expandido")

            // Corrector clásico sobre una zona.
            runOnUiThread { viewModel.openTool(Tool.HEAL) }
            waitUntil(timeoutMillis = 30_000L) { viewModel.uiState.value.activeTool == Tool.HEAL && !viewModel.uiState.value.isProcessing }
            assertEquals(HealEngine.CLASSIC, viewModel.uiState.value.healEngine)
            runOnUiThread { viewModel.addHealStroke(HealStroke(listOf(300f, 900f, 700f, 1200f), 40f)) }
            Thread.sleep(500)
            waitUntil(timeoutMillis = 120_000L) { !viewModel.uiState.value.isProcessing }
            shot("06_corrector")
            // Los pinceles para retocar a mano.
            listOf(HealEngine.MIX, HealEngine.SHUFFLE).forEach { engine ->
                runOnUiThread {
                    viewModel.setHealEngine(engine)
                    viewModel.addHealStroke(HealStroke(listOf(200f, 500f, 800f, 700f), 60f))
                }
                Thread.sleep(500)
                waitUntil(timeoutMillis = 60_000L) { !viewModel.uiState.value.isProcessing }
                assertEquals(engine, viewModel.uiState.value.working.content.heals.last().engine)
                shot("06_pincel_${engine.name}")
            }
            runOnUiThread { viewModel.applyTool() }
            waitUntil(timeoutMillis = 60_000L) { !viewModel.uiState.value.isProcessing }

            runOnUiThread { viewModel.showExportSheet(true) }
            Thread.sleep(600)
            shot("07_guardar")

            // Copia: junto a la original y con la resolución pedida.
            runOnUiThread { viewModel.saveToGallery() }
            waitUntil(timeoutMillis = 60_000L) { !viewModel.uiState.value.isExporting && !viewModel.uiState.value.showExportSheet }
            val copy = original.parentFile.listFiles()!!.single { it.name.startsWith("WallpaperEditor_") }
            val size = viewModel.uiState.value.committed.fit.size
            assertEquals(size.width to size.height, sizeOf(copy))

            // Reemplazar: mismo archivo y misma fecha, para que la carpeta siga en orden.
            val date = 1_600_000_000_000L
            original.setLastModified(date)
            runOnUiThread {
                viewModel.setSaveMode(SaveMode.REPLACE)
                viewModel.replaceOriginal()
            }
            Thread.sleep(300)
            waitUntil(timeoutMillis = 60_000L) { !viewModel.uiState.value.isExporting }
            assertEquals(size.width to size.height, sizeOf(original))
            assertEquals(date, original.lastModified())
            assertTrue(original.parentFile.listFiles()!!.none { it.name.endsWith(".tmp") })
        }
        assertTrue(BitmapFactory.decode(original.readBytes(), null) != null)
    }

    /** Una foto girada por EXIF se abre derecha, y se entienden JPEG, PNG y WebP. */
    @Test
    fun abreFormatosYOrientacion() {
        val pixels = IntArray(40 * 20) { -0x10000 }
        val bitmap = Bitmap.createBitmap(pixels, 40, 20, Bitmap.Config.ARGB_8888)
        listOf(Bitmap.CompressFormat.JPEG, Bitmap.CompressFormat.PNG, Bitmap.CompressFormat.WEBP).forEach { format ->
            val decoded = BitmapFactory.decode(ImageEncoder.encode(bitmap, format, 90), null)!!
            assertEquals(40, decoded.width)
            assertEquals(20, decoded.height)
        }
        val jpeg = ImageEncoder.encode(bitmap, Bitmap.CompressFormat.JPEG, 90)
        // Orientación 6 (girar 90°): Exif mínimo en la cabecera del JPEG.
        val tiff = byteArrayOf(0x4D, 0x4D, 0, 0x2A, 0, 0, 0, 8, 0, 1, 0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, 6, 0, 0, 0, 0, 0, 0, 0, 0)
        val exif = "Exif\u0000\u0000".toByteArray() + tiff
        val length = exif.size + 2
        val rotated = jpeg.copyOfRange(0, 2) + byteArrayOf(-1, 0xE1.toByte(), (length shr 8).toByte(), length.toByte()) + exif + jpeg.copyOfRange(2, jpeg.size)
        val decoded = BitmapFactory.decode(rotated, null)!!
        assertEquals(20, decoded.width)
        assertEquals(40, decoded.height)
    }

    /**
     * Cambia de verdad el fondo de Windows y lo deja como estaba. Solo con WE_REAL_WALLPAPER=1, y
     * solo si el fondo actual es una imagen que se puede volver a poner.
     */
    @Test
    fun cambiaElFondoDeWindows() {
        if (System.getenv("WE_REAL_WALLPAPER") != "1") return
        fun current() = ProcessBuilder("reg", "query", "HKCU\\Control Panel\\Desktop", "/v", "WallPaper").start()
            .inputStream.bufferedReader().readText().substringAfter("REG_SZ").trim()
        val before = File(current())
        assertTrue("El fondo actual no se podría restaurar: $before", before.isFile)
        val pixels = IntArray(1920 * 1080) { -0xff8080 }
        val bitmap = Bitmap.createBitmap(pixels, 1920, 1080, Bitmap.Config.ARGB_8888)
        try {
            kotlinx.coroutines.runBlocking { WallpaperSetter.apply(Activity(), bitmap, WallpaperTarget.HOME) }
            assertTrue(current().contains("wallpaper_"))
        } finally {
            WindowsWallpaper.set(before)
        }
        assertEquals(before.path, current())
    }

    companion object {
        /** Una sola carpeta para todas las pruebas: ni los datos ni la carpeta Imágenes reales de este PC. */
        private val root = Files.createTempDirectory("wallpaper-editor-tour").toFile().also {
            System.setProperty("user.home", it.path)
            System.setProperty("we.data", File(it, "data").path)
        }

        @JvmStatic
        @AfterClass
        fun clean() {
            root.deleteRecursively()
        }
    }
}
