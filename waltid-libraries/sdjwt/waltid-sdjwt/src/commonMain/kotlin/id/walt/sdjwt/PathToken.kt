package id.walt.sdjwt

/**
 * One segment of a dotted JSON path used by [SDMap.generateSDMap]. A path string is split on
 * `.` into segments, each of which is one of three forms:
 * - [Key] — a named object property (e.g. `firstName`)
 * - [Index] — a specific array index, written `[N]` for some non-negative integer N
 * - [Wildcard] — every element of an array, written `[]`
 */
internal sealed class PathToken {
    data class Key(val name: String) : PathToken()
    data class Index(val value: Int) : PathToken()
    object Wildcard : PathToken()

    companion object {
        /**
         * Tokenise a dotted JSON path into its constituent [PathToken]s.
         *
         * @throws IllegalArgumentException if any segment is empty or malformed.
         */
        fun tokenize(path: String): List<PathToken> = path.split(".").map { segment ->
            when {
                segment.isEmpty() ->
                    throw IllegalArgumentException("Empty path segment in '$path'")
                segment == "[]" ->
                    Wildcard
                segment.startsWith("[") && segment.endsWith("]") -> {
                    val index = segment.substring(1, segment.length - 1).toIntOrNull()
                        ?: throw IllegalArgumentException("Malformed array index segment: '$segment'")
                    if (index < 0) throw IllegalArgumentException("Array index must be non-negative: '$segment'")
                    Index(index)
                }
                segment.contains('[') || segment.contains(']') ->
                    throw IllegalArgumentException("Malformed path segment: '$segment'")
                else -> Key(segment)
            }
        }
    }
}

/**
 * Given a list of paths (each a list of remaining tokens at the current depth), drop any
 * paths that have already terminated and group the rest by their first token, mapping each
 * member to its tail (the tokens beyond the first).
 */
internal fun List<List<PathToken>>.groupByFirstToken(): Map<PathToken, List<List<PathToken>>> =
    filter { tokens -> tokens.isNotEmpty() }
        .groupBy({ tokens -> tokens.first() }, { tokens -> tokens.drop(1) })
