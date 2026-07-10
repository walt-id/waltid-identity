package id.walt.walletdemo.compose.logic

internal data class ClaimPathExpression(
    val segments: List<Segment>,
) {
    val leafKey: String? = segments.asReversed()
        .firstNotNullOfOrNull { segment -> (segment as? Segment.Key)?.value }

    sealed interface Segment {
        data class Key(val value: String) : Segment
        data class Index(val value: Int) : Segment
        data object Wildcard : Segment
    }

    companion object {
        fun parse(rawValue: String): ClaimPathExpression =
            ClaimPathExpressionParser(rawValue).parse()
    }
}

private class ClaimPathExpressionParser(rawValue: String) {
    private val path = rawValue.trim()
    private var index = 0
    private val segments = mutableListOf<ClaimPathExpression.Segment>()

    fun parse(): ClaimPathExpression {
        while (index < path.length) {
            when (path[index]) {
                '$', '.', ' ', '\n', '\r', '\t' -> index += 1
                '[' -> addBracketSegment()
                else -> addKey(readDotSegment())
            }
        }
        return ClaimPathExpression(segments)
    }

    private fun addBracketSegment() {
        index += 1
        skipWhitespace()
        val segment = if (index < path.length && path[index].isQuote()) {
            readQuotedSegment(path[index])
        } else {
            readUnquotedBracketSegment()
        }
        skipUntilBracketEnd()
        addToken(segment)
    }

    private fun addToken(rawSegment: String) {
        val segment = rawSegment.trim()
        when {
            segment.isBlank() -> Unit
            segment == "*" -> segments += ClaimPathExpression.Segment.Wildcard
            segment.toIntOrNull() != null -> segments += ClaimPathExpression.Segment.Index(segment.toInt())
            else -> addKey(segment)
        }
    }

    private fun addKey(rawSegment: String) {
        val segment = rawSegment.trim()
        if (segment.isNotBlank()) {
            segments += ClaimPathExpression.Segment.Key(segment)
        }
    }

    private fun skipWhitespace() {
        while (index < path.length && path[index].isWhitespace()) index += 1
    }

    private fun skipUntilBracketEnd() {
        while (index < path.length && path[index] != ']') index += 1
        if (index < path.length) index += 1
    }

    private fun readDotSegment(): String {
        val start = index
        while (index < path.length && path[index] != '.' && path[index] != '[') index += 1
        return path.substring(start, index)
    }

    private fun readUnquotedBracketSegment(): String {
        val start = index
        while (index < path.length && path[index] != ']') index += 1
        return path.substring(start, index)
    }

    private fun readQuotedSegment(quote: Char): String {
        index += 1
        val segment = StringBuilder()
        while (index < path.length) {
            val char = path[index]
            when {
                char == '\\' && index + 1 < path.length -> {
                    index += 1
                    segment.append(path[index])
                    index += 1
                }
                char == quote -> {
                    index += 1
                    return segment.toString()
                }
                else -> {
                    segment.append(char)
                    index += 1
                }
            }
        }
        return segment.toString()
    }
}

private fun Char.isQuote(): Boolean = this == '\'' || this == '"'
