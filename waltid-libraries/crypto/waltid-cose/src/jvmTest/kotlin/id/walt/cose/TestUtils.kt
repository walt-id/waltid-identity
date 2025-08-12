package id.walt.cose

inline fun <T> Iterable<T>.forEachNumbered(action: (index: Int, total: Int, T) -> Unit) =
    count().let { count ->
        forEachIndexed { idx, item ->
            action(idx + 1, count, item)
        }
    }

inline fun <T, R> Iterable<T>.mapNumbered(transform: (index: Int, total: Int, T) -> R): List<R> =
    count().let { count ->
        mapIndexed { idx, item -> transform(idx + 1, count, item) }
}
