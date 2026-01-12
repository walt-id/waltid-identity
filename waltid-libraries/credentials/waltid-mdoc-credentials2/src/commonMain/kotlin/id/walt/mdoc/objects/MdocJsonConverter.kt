package id.walt.mdoc.objects

import id.walt.crypto.utils.Base64Utils.base64UrlDecode
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.LocalDate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.*
import kotlinx.serialization.json.JsonElement
import kotlin.time.Instant

/**
 * Conversion mode for JSON to mdoc value conversion.
 * 
 * - STRICT: Requires registered serializers for all elements. Throws errors for unknown types.
 *   Use for issuance and digest-critical operations where type safety is essential.
 * - LENIENT: Allows fallbacks and best-effort conversion. Use for tools/preview scenarios.
 */
enum class ConversionMode {
    STRICT,
    LENIENT
}

/**
 * Utility for converting JSON elements to appropriate mdoc data types using registered serializers.
 * 
 * This provides a robust, type-safe way to convert JSON input (e.g., from API requests) to the
 * correct Kotlin types expected by the mdoc library, leveraging the MdocsCborSerializer registry.
 * 
 * Key features:
 * - STRICT mode (default): Ensures type safety by requiring registered serializers
 * - LENIENT mode: Allows fallbacks for preview/tool scenarios
 * - Deterministic CBOR output: Uses registered serializers to ensure stable encoding
 * - No type guessing: Only uses registered serializers for top-level elements
 * - Base64/base64url support: Prefers standard string encodings for ByteArray fields
 */
object MdocJsonConverter {
    private val log = KotlinLogging.logger {}

    /**
     * Converts a JSON element to the appropriate mdoc value type for a given namespace and element identifier.
     * 
     * Uses STRICT mode by default (required for issuance/digest-critical operations).
     * 
     * @param jsonElement The JSON element to convert
     * @param namespace The mdoc namespace (e.g., "org.iso.18013.5.1")
     * @param elementIdentifier The element identifier within the namespace
     * @param mode Conversion mode (default: STRICT)
     * @return The converted value as Any (will be the correct type based on the serializer)
     * @throws IllegalArgumentException if the JSON cannot be converted (in STRICT mode) or no serializer is found
     */
    fun toMdocValue(
        jsonElement: JsonElement,
        namespace: String,
        elementIdentifier: String,
        mode: ConversionMode = ConversionMode.STRICT
    ): Any {
        return when (jsonElement) {
            is JsonPrimitive -> convertPrimitive(jsonElement, namespace, elementIdentifier, mode)
            is JsonArray -> convertArray(jsonElement, namespace, elementIdentifier, mode)
            is JsonObject -> convertObject(jsonElement, namespace, elementIdentifier, mode)
            is JsonNull -> throw IllegalArgumentException("Null values are not supported in mdoc data")
        }
    }

    /**
     * Converts a JSON primitive to the appropriate mdoc value.
     */
    private fun convertPrimitive(
        json: JsonPrimitive,
        namespace: String,
        elementIdentifier: String,
        mode: ConversionMode
    ): Any {
        // Check for registered serializer first
        val serializer = MdocsCborSerializer.lookupSerializer(namespace, elementIdentifier)
        if (serializer != null) {
            return try {
                @Suppress("UNCHECKED_CAST")
                Json.decodeFromJsonElement(serializer as KSerializer<Any>, json)
            } catch (e: Exception) {
                if (mode == ConversionMode.STRICT) {
                    throw IllegalArgumentException(
                        "Failed to deserialize primitive using registered serializer for $namespace.$elementIdentifier: ${e.message}",
                        e
                    )
                }
                log.warn(e) {
                    "Failed to deserialize primitive using registered serializer for $namespace.$elementIdentifier, falling back"
                }
                convertPrimitiveDefault(json, mode)
            }
        }

        // No serializer found - check if it's a standard primitive type
        // In STRICT mode, standard primitives (String, Int, Long, Boolean, Double, Float) are allowed
        // because they have deterministic CBOR encoding
        val isStandardPrimitive = when {
            json.isString -> true
            json.booleanOrNull != null -> true
            json.intOrNull != null -> true
            json.longOrNull != null -> true
            json.doubleOrNull != null -> true
            json.floatOrNull != null -> true
            else -> false
        }

        if (isStandardPrimitive) {
            // Standard primitives are safe in STRICT mode - they have deterministic CBOR encoding
            return convertPrimitiveDefault(json, mode)
        }

        // Non-standard primitive (ambiguous type)
        if (mode == ConversionMode.STRICT) {
            throw IllegalArgumentException(
                "No serializer registered for $namespace.$elementIdentifier and value is not a standard primitive type. " +
                        "STRICT mode requires registered serializers for complex types or explicit standard primitives (String, Int, Long, Boolean, Double, Float)."
            )
        }

        // LENIENT mode: fallback to default conversion
        return convertPrimitiveDefault(json, mode)
    }

    /**
     * Default primitive conversion when no serializer is registered (LENIENT mode only).
     * 
     * Preserves numeric types to avoid lossy conversions:
     * - Integers stay as Int or Long
     * - Decimals stay as Double (not coerced to Long)
     */
    private fun convertPrimitiveDefault(json: JsonPrimitive, mode: ConversionMode): Any {
        return when {
            json.isString -> json.content
            json.booleanOrNull != null -> json.boolean
            json.intOrNull != null -> json.int
            json.longOrNull != null -> json.long
            json.doubleOrNull != null -> json.double // Keep as Double, don't coerce to Long
            json.floatOrNull != null -> json.float
            else -> {
                // Ambiguous primitive - don't silently convert to string
                if (mode == ConversionMode.STRICT) {
                    throw IllegalArgumentException(
                        "Cannot determine type for primitive value: ${json.content}. " +
                                "STRICT mode requires explicit type information."
                    )
                }
                // LENIENT mode: log warning and return as string (but this is not ideal)
                log.warn { "Ambiguous primitive value converted to string in LENIENT mode: ${json.content}" }
                json.content
            }
        }
    }

    /**
     * Converts a JSON array to the appropriate mdoc value.
     * 
     * Only uses registered serializer for the top-level element.
     * Nested elements are not guessed - they must be handled by the serializer.
     */
    private fun convertArray(
        json: JsonArray,
        namespace: String,
        elementIdentifier: String,
        mode: ConversionMode
    ): Any {
        // Check for registered serializer first
        val serializer = MdocsCborSerializer.lookupSerializer(namespace, elementIdentifier)
        if (serializer != null) {
            // Check if this is a ByteArray serializer
            val byteArraySerializerDescriptor = ByteArraySerializer().descriptor
            val isByteArraySerializer = serializer.descriptor.serialName == byteArraySerializerDescriptor.serialName ||
                    serializer.descriptor.serialName.contains("ByteArray", ignoreCase = true) ||
                    serializer.descriptor.serialName == "kotlin.ByteArray"

            if (isByteArraySerializer) {
                // ByteArray can come as:
                // 1. Base64/base64url encoded string (preferred)
                // 2. Array of numbers (legacy format)
                return when {
                    // Check if it's a single string (base64/base64url)
                    json.size == 1 && json[0] is JsonPrimitive && (json[0] as JsonPrimitive).isString -> {
                        try {
                            val base64String = (json[0] as JsonPrimitive).content
                            // Try base64url first, then base64
                            base64String.base64UrlDecode()
                        } catch (e: Exception) {
                            throw IllegalArgumentException(
                                "Invalid base64/base64url encoding for ByteArray field $namespace.$elementIdentifier: ${e.message}",
                                e
                            )
                        }
                    }
                    // Legacy: array of numbers
                    json.all { it is JsonPrimitive && (it.intOrNull != null || it.longOrNull != null) } -> {
                        try {
                            json.mapNotNull {
                                when (it) {
                                    is JsonPrimitive -> it.intOrNull?.toByte() ?: it.longOrNull?.toByte()
                                    else -> null
                                }
                            }.toByteArray()
                        } catch (e: Exception) {
                            throw IllegalArgumentException(
                                "Failed to convert array to ByteArray for $namespace.$elementIdentifier: ${e.message}",
                                e
                            )
                        }
                    }
                    else -> {
                        throw IllegalArgumentException(
                            "Invalid format for ByteArray field $namespace.$elementIdentifier: " +
                                    "expected base64/base64url string or array of numbers"
                        )
                    }
                }
            }

            // Check if this is a list serializer
            if (serializer.descriptor.kind is StructureKind.LIST) {
                return try {
                    @Suppress("UNCHECKED_CAST")
                    Json.decodeFromJsonElement(serializer as KSerializer<Any>, json)
                } catch (e: Exception) {
                    if (mode == ConversionMode.STRICT) {
                        throw IllegalArgumentException(
                            "Failed to deserialize array using registered serializer for $namespace.$elementIdentifier: ${e.message}",
                            e
                        )
                    }
                    log.warn(e) {
                        "Failed to deserialize array using registered serializer for $namespace.$elementIdentifier, falling back"
                    }
                    convertArrayDefault(json, mode)
                }
            }
        }

        // No serializer found
        if (mode == ConversionMode.STRICT) {
            throw IllegalArgumentException(
                "No serializer registered for array element $namespace.$elementIdentifier. " +
                        "STRICT mode requires all elements to have registered serializers."
            )
        }

        // LENIENT mode: convert as generic list
        // Note: We don't reuse elementIdentifier for nested elements - they're converted without type hints
        return convertArrayDefault(json, mode)
    }

    /**
     * Default array conversion when no serializer is registered (LENIENT mode only).
     * 
     * Converts elements without reusing the parent elementIdentifier.
     */
    private fun convertArrayDefault(
        json: JsonArray,
        mode: ConversionMode
    ): List<Any> {
        // Don't reuse elementIdentifier for nested elements - convert without type hints
        return json.map { element ->
            when (element) {
                is JsonPrimitive -> convertPrimitiveDefault(element, mode)
                is JsonArray -> convertArrayDefault(element, mode)
                is JsonObject -> convertObjectDefault(element, mode)
                is JsonNull -> throw IllegalArgumentException("Null values are not supported in mdoc data")
            }
        }
    }

    /**
     * Converts a JSON object to the appropriate mdoc value.
     * 
     * Only uses registered serializer for the top-level element.
     * Nested fields are not guessed - they must be handled by the serializer.
     */
    private fun convertObject(
        json: JsonObject,
        namespace: String,
        elementIdentifier: String,
        mode: ConversionMode
    ): Any {
        // Check for registered serializer first
        val serializer = MdocsCborSerializer.lookupSerializer(namespace, elementIdentifier)
        if (serializer != null) {
            return try {
                @Suppress("UNCHECKED_CAST")
                Json.decodeFromJsonElement(serializer as KSerializer<Any>, json)
            } catch (e: Exception) {
                if (mode == ConversionMode.STRICT) {
                    throw IllegalArgumentException(
                        "Failed to deserialize object using registered serializer for $namespace.$elementIdentifier: ${e.message}",
                        e
                    )
                }
                log.warn(e) {
                    "Failed to deserialize object using registered serializer for $namespace.$elementIdentifier, falling back"
                }
                convertObjectDefault(json, mode)
            }
        }

        // No serializer found
        if (mode == ConversionMode.STRICT) {
            throw IllegalArgumentException(
                "No serializer registered for object element $namespace.$elementIdentifier. " +
                        "STRICT mode requires all elements to have registered serializers."
            )
        }

        // LENIENT mode: convert to Map
        return convertObjectDefault(json, mode)
    }

    /**
     * Default object conversion when no serializer is registered (LENIENT mode only).
     * 
     * Converts nested fields without reusing the parent elementIdentifier.
     */
    private fun convertObjectDefault(
        json: JsonObject,
        mode: ConversionMode
    ): Map<String, Any> {
        // Don't reuse elementIdentifier for nested fields - convert without type hints
        return json.mapValues { (_, value) ->
            when (value) {
                is JsonPrimitive -> convertPrimitiveDefault(value, mode)
                is JsonArray -> convertArrayDefault(value, mode)
                is JsonObject -> convertObjectDefault(value, mode)
                is JsonNull -> throw IllegalArgumentException("Null values are not supported in mdoc data")
            }
        }
    }
}

