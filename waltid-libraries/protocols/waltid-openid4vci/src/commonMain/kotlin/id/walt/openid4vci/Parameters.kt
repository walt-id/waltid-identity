package id.walt.openid4vci

/**
 * Mutable collection of request parameters with multi-value support.
 */
class Parameters(
    initial: Map<String, String> = emptyMap()
) : Iterable<Pair<String, String>> {

    private val values: MutableMap<String, MutableList<String>> =
        initial.entries.groupBy({ it.key }, { it.value }).mapValues { (_, v) -> v.toMutableList() }.toMutableMap()

    fun getFirst(name: String): String? = values[name]?.firstOrNull()

    fun getAll(name: String): List<String> = values[name]?.toList() ?: emptyList()

    fun set(name: String, value: String) {
        values[name] = mutableListOf(value)
    }

    fun add(name: String, value: String) {
        values.getOrPut(name) { mutableListOf() }.add(value)
    }

    fun addAll(params: Map<String, String>) {
        params.forEach { (key, value) -> set(key, value) }
    }

    fun remove(name: String) {
        values.remove(name)
    }

    fun toSingleValueMap(): Map<String, String> = values.mapValues { it.value.firstOrNull().orEmpty() }

    override fun iterator(): Iterator<Pair<String, String>> =
        values.entries.flatMap { (key, list) -> list.map { key to it } }.iterator()
}
