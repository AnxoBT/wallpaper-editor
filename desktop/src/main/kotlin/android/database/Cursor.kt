package android.database

import java.io.Closeable

/** Resultado de una consulta: filas ya leídas, con las columnas en el orden pedido. */
class Cursor(private val rows: List<List<Any?>>) : Closeable {
    private var position = -1

    fun moveToNext(): Boolean = ++position < rows.size

    fun moveToFirst(): Boolean {
        position = 0
        return rows.isNotEmpty()
    }

    fun getString(column: Int): String? = rows[position][column]?.toString()

    fun getLong(column: Int): Long = (rows[position][column] as? Number)?.toLong() ?: 0L

    fun getInt(column: Int): Int = getLong(column).toInt()

    override fun close() = Unit
}

abstract class ContentObserver(handler: android.os.Handler?) {
    abstract fun onChange(selfChange: Boolean)
}
