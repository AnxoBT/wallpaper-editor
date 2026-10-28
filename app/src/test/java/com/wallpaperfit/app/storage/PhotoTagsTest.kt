package com.wallpaperfit.app.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoTagsTest {

    private val ghibli = setOf("ghibli", "anime", "peliculas")

    @Test
    fun `varias etiquetas marcadas exigen todas`() {
        assertTrue(PhotoTags.matches(ghibli, setOf("anime", "peliculas"), ""))
        assertFalse(PhotoTags.matches(ghibli, setOf("anime", "series"), ""))
    }

    @Test
    fun `el texto busca dentro de las etiquetas sin tildes ni mayusculas`() {
        assertTrue(PhotoTags.matches(setOf("animación"), emptySet(), "ANIMACION"))
        assertTrue(PhotoTags.matches(ghibli, setOf("anime"), "pelic"))
        assertFalse(PhotoTags.matches(ghibli, setOf("anime"), "manga"))
    }

    @Test
    fun `sin filtro pasan todas`() {
        assertTrue(PhotoTags.matches(emptySet(), emptySet(), " "))
    }

    @Test
    fun `cuenta las etiquetas de mas a menos usada`() {
        val counts = PhotoTags.counts(listOf(ghibli, setOf("anime", "manga")))
        assertEquals("anime" to 2, counts.first())
        assertEquals(4, counts.size)
    }

    @Test
    fun `una etiqueta marcada se ofrece aunque ya no le queden fotos`() {
        val offered = PhotoTags.selectedFirst(listOf("anime" to 5, "manga" to 2), setOf("manga", "revisar"))
        assertEquals(listOf("manga" to 2, "revisar" to 0, "anime" to 5), offered)
    }
}
