package android.util

/** Registro de la app: a la salida de error, para verlo si se abre desde una consola. */
object Log {
    fun i(tag: String, message: String): Int = 0.also { System.err.println("I/$tag: $message") }

    fun w(tag: String, message: String, error: Throwable? = null): Int = 0.also { System.err.println("W/$tag: $message ${error ?: ""}") }

    fun e(tag: String, message: String, error: Throwable? = null): Int = 0.also {
        System.err.println("E/$tag: $message")
        error?.printStackTrace()
    }
}

data class Size(val width: Int, val height: Int)

open class LruCache<K : Any, V : Any>(private val maxSize: Int) {
    private val map = LinkedHashMap<K, V>(16, 0.75f, true)
    private var size = 0

    protected open fun sizeOf(key: K, value: V): Int = 1

    @Synchronized
    fun get(key: K): V? = map[key]

    @Synchronized
    fun put(key: K, value: V): V? {
        val previous = map.put(key, value)
        size += sizeOf(key, value) - (previous?.let { sizeOf(key, it) } ?: 0)
        val entries = map.entries.iterator()
        while (size > maxSize && entries.hasNext()) {
            val eldest = entries.next()
            if (eldest.key == key) continue
            size -= sizeOf(eldest.key, eldest.value)
            entries.remove()
        }
        return previous
    }
}
