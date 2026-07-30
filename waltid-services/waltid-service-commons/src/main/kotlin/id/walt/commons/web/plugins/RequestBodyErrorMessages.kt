@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.commons.web.plugins

import io.ktor.serialization.JsonConvertException
import io.ktor.server.plugins.BadRequestException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.serializer
import kotlin.reflect.full.createType

/**
 * Human-readable summary of a request-body deserialization failure for API clients.
 */
data class RequestBodyErrorInfo(
    val message: String,
    val details: Map<String, JsonElement> = emptyMap(),
) {
    fun detailsJsonObject() = buildJsonObject {
        details.forEach { (key, value) -> put(key, value) }
    }
}

private val FAILED_TO_CONVERT_REGEX =
    Regex("""Failed to convert request body to (?:class\s+)?([^\s,]+)""")
private val UNKNOWN_KEY_REGEX =
    Regex("""Encountered(?: an)? unknown key ['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE)
private val POLYMORPHIC_SCOPE_REGEX =
    Regex("""polymorphic scope of ['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE)
private val DISCRIMINATOR_FIELD_REGEX =
    Regex(
        """(?:class\s+)?discriminator(?:\s+field)?\s+['"]([^'"]+)['"]""",
        RegexOption.IGNORE_CASE,
    )
private val JSON_INPUT_SUFFIX_REGEX =
    Regex("""\s*JSON input:.*$""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

/**
 * Returns a cleaned client-facing error when [cause] (or its cause chain) is a
 * request-body conversion / JSON deserialization failure; otherwise `null`.
 */
fun humanizeRequestBodyError(cause: Throwable): RequestBodyErrorInfo? {
    if (!isRequestBodyConversionFailure(cause)) return null

    val expectedType = extractExpectedType(cause)
    val missingFieldCause = findInCauseChain(cause) { it is MissingFieldException } as? MissingFieldException
    val serializationCause = findInCauseChain(cause) {
        it is SerializationException || it is JsonConvertException
    }

    return when {
        missingFieldCause != null -> missingFieldsError(missingFieldCause, expectedType)
        serializationCause != null && isUnknownKeyError(serializationCause.message) ->
            unknownKeysError(serializationCause.message!!, expectedType)
        serializationCause != null && isMissingDiscriminatorError(serializationCause.message) ->
            missingDiscriminatorError(serializationCause.message!!, expectedType, cause)
        serializationCause != null -> {
            val cleaned = cleanSerializationMessage(serializationCause.message)
            RequestBodyErrorInfo(
                message = cleaned ?: "Failed to parse request body${expectedType?.let { " as $it" } ?: ""}",
                details = expectedTypeDetails(expectedType),
            )
        }
        else -> RequestBodyErrorInfo(
            message = "Failed to convert request body${expectedType?.let { " to $it" } ?: ""}",
            details = expectedTypeDetails(expectedType),
        )
    }
}

fun isRequestBodyConversionFailure(cause: Throwable): Boolean {
    val messages = causeChain(cause).mapNotNull { it.message }
    if (messages.any {
            it.contains("Failed to convert request body") ||
                it.contains("Unable to convert the supplied request body")
        }
    ) {
        return true
    }
    if (findInCauseChain(cause) { it is JsonConvertException } != null) return true
    return cause is BadRequestException &&
        findInCauseChain(cause) { it is SerializationException } != null
}

private fun missingFieldsError(
    exception: MissingFieldException,
    expectedType: String?,
): RequestBodyErrorInfo {
    val fields = exception.missingFields
    val typeName = expectedType
        ?: exception.serialName?.substringAfterLast('.')
            ?.takeIf { it.isNotBlank() }
    val fieldList = fields.joinToString(", ")
    return RequestBodyErrorInfo(
        message = "Request body is missing required field(s): $fieldList",
        details = buildMap {
            put("missingFields", JsonArray(fields.map { JsonPrimitive(it) }))
            typeName?.let { put("expectedType", JsonPrimitive(it)) }
        },
    )
}

private fun unknownKeysError(rawMessage: String, expectedType: String?): RequestBodyErrorInfo {
    val keys = UNKNOWN_KEY_REGEX.findAll(rawMessage).map { it.groupValues[1] }.distinct().toList()
    val fieldList = keys.joinToString(", ")
    return RequestBodyErrorInfo(
        message = if (keys.isNotEmpty()) {
            "Request body contains unknown field(s): $fieldList"
        } else {
            "Request body contains unknown field(s)"
        },
        details = buildMap {
            if (keys.isNotEmpty()) {
                put("unknownFields", JsonArray(keys.map { JsonPrimitive(it) }))
            }
            expectedType?.let { put("expectedType", JsonPrimitive(it)) }
        },
    )
}

private fun missingDiscriminatorError(
    rawMessage: String,
    expectedType: String?,
    rootCause: Throwable,
): RequestBodyErrorInfo {
    val typeName = expectedType
        ?: POLYMORPHIC_SCOPE_REGEX.find(rawMessage)?.groupValues?.get(1)?.substringAfterLast('.')
    val discriminator = DISCRIMINATOR_FIELD_REGEX.find(rawMessage)?.groupValues?.get(1)
        ?: resolveDiscriminatorField(rootCause, typeName)

    val message = when {
        discriminator != null && typeName != null ->
            "Request body is missing required discriminator field '$discriminator' for $typeName"
        discriminator != null ->
            "Request body is missing required discriminator field '$discriminator'"
        typeName != null ->
            "Request body is missing required class discriminator for $typeName"
        else ->
            "Request body is missing required class discriminator"
    }

    return RequestBodyErrorInfo(
        message = message,
        details = buildMap {
            typeName?.let { put("expectedType", JsonPrimitive(it)) }
            discriminator?.let { put("discriminator", JsonPrimitive(it)) }
        },
    )
}

private fun expectedTypeDetails(expectedType: String?): Map<String, JsonElement> =
    expectedType?.let { mapOf("expectedType" to JsonPrimitive(it)) } ?: emptyMap()

private fun extractExpectedType(cause: Throwable): String? {
    for (throwable in causeChain(cause)) {
        val message = throwable.message ?: continue
        FAILED_TO_CONVERT_REGEX.find(message)?.groupValues?.get(1)
            ?.substringAfterLast('.')
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        Regex("""expected type:\s*([A-Za-z0-9_.$]+)""", RegexOption.IGNORE_CASE)
            .find(message)
            ?.groupValues?.get(1)
            ?.substringAfterLast('.')
            ?.let { return it }

        POLYMORPHIC_SCOPE_REGEX.find(message)?.groupValues?.get(1)
            ?.substringAfterLast('.')
            ?.let { return it }

        if (throwable is MissingFieldException) {
            throwable.serialName?.substringAfterLast('.')
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
        }
    }
    return null
}

private fun resolveDiscriminatorField(cause: Throwable, typeSimpleName: String?): String? {
    val fqcn = causeChain(cause).firstNotNullOfOrNull { throwable ->
        val message = throwable.message ?: return@firstNotNullOfOrNull null
        FAILED_TO_CONVERT_REGEX.find(message)?.groupValues?.get(1)
            ?.takeIf { it.contains('.') }
    } ?: return defaultDiscriminatorForKnownType(typeSimpleName)

    return runCatching {
        val kClass = Class.forName(fqcn).kotlin
        val descriptor = serializer(kClass.createType()).descriptor
        discriminatorFromDescriptor(descriptor) ?: "type".takeIf {
            descriptor.kind is PolymorphicKind
        }
    }.getOrNull() ?: defaultDiscriminatorForKnownType(typeSimpleName)
}

private fun discriminatorFromDescriptor(descriptor: SerialDescriptor): String? =
    descriptor.annotations.filterIsInstance<JsonClassDiscriminator>().firstOrNull()?.discriminator

/**
 * Fallback when the FQCN is unavailable but the simple name is a known polymorphic request type.
 */
private fun defaultDiscriminatorForKnownType(typeSimpleName: String?): String? = when (typeSimpleName) {
    "TypedKeyGenerationRequest" -> "backend"
    else -> null
}

private fun isUnknownKeyError(message: String?): Boolean =
    message != null && UNKNOWN_KEY_REGEX.containsMatchIn(message)

private fun isMissingDiscriminatorError(message: String?): Boolean =
    message != null && message.contains("Class discriminator was missing", ignoreCase = true)

private fun cleanSerializationMessage(message: String?): String? {
    if (message.isNullOrBlank()) return null
    return message
        .removePrefix("Illegal input: ")
        .replace(JSON_INPUT_SUFFIX_REGEX, "")
        .trim()
        .takeIf { it.isNotBlank() }
}

private fun causeChain(cause: Throwable): Sequence<Throwable> = sequence {
    var current: Throwable? = cause
    val seen = mutableSetOf<Throwable>()
    while (current != null && seen.add(current)) {
        yield(current)
        current = current.cause
    }
}

private fun findInCauseChain(cause: Throwable, predicate: (Throwable) -> Boolean): Throwable? =
    causeChain(cause).firstOrNull(predicate)
