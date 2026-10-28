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

    @Test
    fun `una etiqueta excluida quita las fotos que la tienen y deja las que no tienen ninguna`() {
        val filter = TagFilter(excluded = setOf("revisar"))
        assertFalse(PhotoTags.matches(setOf("anime", "revisar"), filter, ""))
        assertTrue(PhotoTags.matches(setOf("anime"), filter, ""))
        assertTrue(PhotoTags.matches(emptySet(), filter, ""))
    }

    @Test
    fun `incluir y excluir a la vez`() {
        val filter = TagFilter(included = setOf("anime"), excluded = setOf("revisar"))
        assertTrue(PhotoTags.matches(setOf("anime"), filter, ""))
        assertFalse(PhotoTags.matches(setOf("anime", "revisar"), filter, ""))
        assertFalse(PhotoTags.matches(setOf("manga"), filter, ""))
    }

    @Test
    fun `tres toques incluyen, excluyen y quitan`() {
        val first = TagFilter().cycled("anime")
        assertEquals(TagState.INCLUDED, first.state("anime"))
        val second = first.cycled("anime")
        assertEquals(TagState.EXCLUDED, second.state("anime"))
        assertEquals(TagFilter(), second.cycled("anime"))
    }

    @Test
    fun `solo cuenta las fotos que existen y una vez por cada copia`() {
        val tags = mapOf("a.jpg" to setOf("revisar"), "borrada.jpg" to setOf("revisar"), "b.jpg" to setOf("revisar", "anime"))
        val counts = PhotoTags.countsIn(tags, mapOf("a.jpg" to 1, "b.jpg" to 2)).toMap()
        assertEquals(3, counts["revisar"])
        assertEquals(2, counts["anime"])
    }

    @Test
    fun `una etiqueta sin fotos reales sale con cero`() {
        assertEquals(listOf("vieja" to 0), PhotoTags.countsIn(mapOf("borrada.jpg" to setOf("vieja")), emptyMap()))
    }

    @Test
    fun `las excluidas tambien se ofrecen primero para poder quitarlas`() {
        val offered = PhotoTags.selectedFirst(listOf("anime" to 5, "manga" to 2), setOf("manga"), setOf("revisar"))
        assertEquals(listOf("manga" to 2, "revisar" to 0, "anime" to 5), offered)
    }

    @Test
    fun folderNameStaysInsidePictures() {
        assertEquals("Favoritas", PhotoTags.folderName("favoritas"))
        assertEquals("Star wars", PhotoTags.folderName("star wars"))
        // Una etiqueta importada no puede sacar la copia de Imágenes.
        val evil = PhotoTags.folderName("..\\..\\AppData/../x:y")
        assertFalse(evil.contains("..") || evil.contains("\\") || evil.contains("/") || evil.contains(":"))
        assertEquals("_", PhotoTags.folderName(".."))
    }
}
