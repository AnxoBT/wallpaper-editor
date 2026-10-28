package com.wallpaperfit.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class GridMetricsTest {

    /** 3 columnas, filas de 100 + 10 de separación, pantalla de 500. */
    private fun metrics(total: Int) = GridMetrics.of(
        total = total,
        columns = 3,
        itemHeight = 100,
        spacing = 10,
        padding = 0,
        viewport = 500,
    )

    @Test
    fun `sin nada que desplazar no hay recorrido`() {
        assertEquals(0, metrics(9).maxScroll)
    }

    @Test
    fun `una fila de mas cuenta aunque no este completa`() {
        // 7 filas: 7 * 100 + 6 * 10 = 760.
        assertEquals(260, metrics(19).maxScroll)
    }

    @Test
    fun `el tirador es mas pequeno cuantas mas fotos hay`() {
        assertEquals(500f / 760f, metrics(19).thumbFraction, 0.001f)
        assertEquals(500f / 10990f, metrics(300).thumbFraction, 0.001f)
    }

    @Test
    fun `la posicion va de 0 a 1 segun lo desplazado`() {
        val metrics = metrics(30)
        assertEquals(0f, metrics.progress(0, 0), 0.001f)
        assertEquals(1f, metrics.progress(9, metrics.maxScroll - 330), 0.001f)
    }

    @Test
    fun `saltar a una fraccion da la foto y el resto en pixeles`() {
        // 10 filas: 1090 de contenido, 590 de recorrido; la mitad son 295: fila 2 y 75 px.
        assertEquals(6 to 75, metrics(30).target(0.5f))
        assertEquals(0 to 0, metrics(30).target(0f))
    }
}
