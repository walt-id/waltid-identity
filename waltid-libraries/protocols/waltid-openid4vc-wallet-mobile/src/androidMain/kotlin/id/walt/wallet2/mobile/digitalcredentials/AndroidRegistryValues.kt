package id.walt.wallet2.mobile.digitalcredentials

import androidx.credentials.registry.digitalcredentials.mdoc.MdocField
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

// The platform can select a field by its path without knowing its exact value. Keep
// compound and embedded media values in wallet storage; a null matcher value cannot satisfy
// an exact-value query (MdocField's documented photo behavior). This policy is independent
// of claim names and credential formats and applies to both native registry encodings.
internal fun String.registryScalar(): JsonPrimitive? {
    // Registry records already contain valid JSON. Avoid constructing a potentially large
    // tree for compound values that cannot become scalar matcher values.
    when (firstOrNull { !it.isWhitespace() }) {
        '[', '{' -> return null
    }
    val value = Json.parseToJsonElement(this) as? JsonPrimitive ?: return null
    if (value === JsonNull) return null
    if (value.isString) {
        val prefix = value.content.take(128).trimStart()
        if (prefix.startsWith("data:", ignoreCase = true) ||
            EncodedImagePrefixes.any(prefix::startsWith)
        ) return null
    }
    return value
}

internal fun JsonPrimitive.toPlatformValue(): Any = when {
    isString -> content
    booleanOrNull != null -> requireNotNull(booleanOrNull)
    longOrNull != null -> requireNotNull(longOrNull)
    doubleOrNull != null -> requireNotNull(doubleOrNull)
    else -> content
}

internal fun JsonPrimitive.registryDisplayValue(): String =
    content.take(MAX_FIELD_DISPLAY_LENGTH)

internal const val MAX_MATCHER_VALUE_LENGTH = 128

private const val MAX_FIELD_DISPLAY_LENGTH = 128

private val EncodedImagePrefixes = listOf("iVBORw0KGgo", "/9j/", "_9j_", "R0lGOD", "UklGR")
