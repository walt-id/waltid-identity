package id.walt.issuer2.config

import com.sksamuel.hoplite.*
import com.sksamuel.hoplite.decoder.DotPath
import com.sksamuel.hoplite.parsers.Parser
import com.typesafe.config.*
import java.io.InputStream
import java.io.InputStreamReader

/**
 * HOCON parser that keeps unquoted integers beyond Int64 recognisable.
 *
 * Typesafe Config parses an unquoted integer that does not fit in a Long as an unquoted string, and Hoplite's
 * HoconParser then turns quoted and unquoted strings into the same [StringNode]. This parser mirrors Hoplite's
 * production but tags such values with [LARGE_INTEGER_META], so [Issuer2MetadataJsonDecoder] can emit the original digits
 * as a JSON number while values written as strings stay strings.
 */
internal class Issuer2MetadataHoconParser : Parser {

    override fun load(input: InputStream, source: String): Node {
        val config = ConfigFactory.parseReader(InputStreamReader(input)).resolve()
        return config.root().toNode(source, DotPath.root)
    }

    override fun defaultFileExtensions(): List<String> = listOf("conf")

    private fun ConfigValue.toNode(source: String, path: DotPath): Node {
        val pos = Pos.LinePos(origin().lineNumber(), source)
        return when (valueType()) {
            ConfigValueType.OBJECT -> MapNode(
                (this as ConfigObject).entries.associate { (key, value) -> key to value.toNode(source, path.with(key)) },
                pos,
                path,
            )
            ConfigValueType.LIST -> ArrayNode((this as ConfigList).map { it.toNode(source, path) }, pos, path)
            ConfigValueType.NUMBER -> when (val number = unwrapped()) {
                is Double -> DoubleNode(number, pos, path)
                is Float -> DoubleNode(number.toDouble(), pos, path)
                is Long -> LongNode(number, pos, path)
                is Int -> LongNode(number.toLong(), pos, path)
                else -> throw RuntimeException("Unexpected element type for ConfigValueType.NUMBER: $number")
            }
            ConfigValueType.BOOLEAN -> BooleanNode(unwrapped() as Boolean, pos, path)
            ConfigValueType.STRING -> {
                val value = unwrapped().toString()
                val meta = if (isUnquoted() && isIntegerBeyondInt64(value)) mapOf(LARGE_INTEGER_META to true) else emptyMap()
                StringNode(value, pos, path, meta)
            }
            ConfigValueType.NULL -> NullNode(pos, path)
        }
    }

    /** Typesafe Config only exposes quoting through its internal `ConfigString.Unquoted` class. */
    private fun ConfigValue.isUnquoted(): Boolean = javaClass.name == UNQUOTED_STRING_CLASS

    companion object {
        /** [StringNode] meta key marking an unquoted HOCON integer beyond Int64. */
        const val LARGE_INTEGER_META = "waltid.largeInteger"

        internal const val UNQUOTED_STRING_CLASS = "com.typesafe.config.impl.ConfigString\$Unquoted"

        private val integer = Regex("-?(0|[1-9][0-9]*)")

        internal fun isIntegerBeyondInt64(value: String): Boolean =
            integer.matches(value) && value.toLongOrNull() == null
    }
}
