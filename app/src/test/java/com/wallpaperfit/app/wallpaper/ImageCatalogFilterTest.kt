package com.wallpaperfit.app.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageCatalogFilterTest {

    private val folder = WallpaperSource("carpeta", isFolder = true, name = "Pins")
    private val ghibli = WallpaperSource.forTag("ghibli")
    private val manga = WallpaperSource.forTag("manga")
    private val names = mapOf("a" to "a.jpg", "b" to "b.jpg", "c" to "c.jpg")
    private val tagged = mapOf("ghibli" to setOf("a.jpg", "b.jpg"), "manga" to setOf("b.jpg", "c.jpg"))

    private fun select(sources: List<WallpaperSource>, active: Set<WallpaperSource> = sources.toSet()): List<String> {
        val bySource = sources.map { source ->
            when {
                source.isFolder -> listOf("a", "b", "c")
                source.tag == "ghibli" -> listOf("galeria-1", "galeria-2")
                else -> listOf("galeria-2", "galeria-3")
            }
        }
        return ImageCatalog.selectImages(sources, bySource, tagged, names, active)
    }

    @Test
    fun `sin etiquetas salen todas las de la carpeta`() {
        assertEquals(listOf("a", "b", "c"), select(listOf(folder)))
    }

    @Test
    fun `una etiqueta deja solo las fotos de la carpeta que la tienen`() {
        assertEquals(listOf("a", "b"), select(listOf(folder, ghibli)))
    }

    @Test
    fun `con varias etiquetas la foto tiene que tenerlas todas`() {
        assertEquals(listOf("b"), select(listOf(folder, ghibli, manga)))
    }

    @Test
    fun `sin carpetas una etiqueta busca en toda la galeria`() {
        assertEquals(listOf("galeria-1", "galeria-2"), select(listOf(ghibli)))
    }

    @Test
    fun `sin carpetas varias etiquetas tambien exigen todas`() {
        assertEquals(listOf("galeria-2"), select(listOf(ghibli, manga)))
    }

    @Test
    fun `una etiqueta fuera de horario no filtra`() {
        assertEquals(listOf("a", "b", "c"), select(listOf(folder, ghibli), active = setOf(folder)))
    }

    @Test
    fun `si la carpeta esta fuera de horario el filtro sigue limitado a ella`() {
        assertEquals(listOf("a", "b"), select(listOf(folder, ghibli), active = setOf(ghibli)))
    }

    @Test
    fun `una etiqueta fuera de horario no se exige`() {
        assertEquals(listOf("a", "b"), select(listOf(folder, ghibli, manga), active = setOf(folder, ghibli)))
    }
}
