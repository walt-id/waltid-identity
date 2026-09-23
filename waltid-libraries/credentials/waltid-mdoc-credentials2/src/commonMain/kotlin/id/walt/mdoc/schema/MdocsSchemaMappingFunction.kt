@file:OptIn(ExperimentalSerializationApi::class, ExperimentalUnsignedTypes::class)

package id.walt.mdoc.schema

import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import io.github.oshai.kotlinlogging.KotlinLogging
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto.utils.JsonUtils.toSerializedJsonElement
import id.walt.mdoc.encoding.toMdocTDateString
import id.walt.mdoc.schema.MdocsSchema.MdocsDatatype.*
import id.walt.mdoc.schema.MdocsSchema.MdocsSchemaType
import kotlinx.datetime.LocalDate
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.*
import kotlinx.serialization.json.*
import kotlin.time.Instant

/**
 * What to do when a `BYTES` element arrives as a JSON array of numbers rather than a base64url string.
 *
 * The array form is what the API accepted historically, so it must keep working for stored credential data and
 * for clients that have not migrated. It is expensive on the way in, though: parsing it allocates a
 * `JsonLiteral` and a `String` per byte - roughly 80 bytes of heap for one byte of payload - so a 230 KB
 * portrait costs tens of megabytes per copy.
 */
enum class ByteArrayInputPolicy {
    /** Accept the array, and log what should be sent instead. The default, so nothing breaks. */
    WARN,

    /** Refuse the array. For deployments that have migrated their clients and want to keep it that way. */
    REJECT,
}

object MdocsSchemaMappingFunction {

    private val log = KotlinLogging.logger { }

    /**
     * Reads a `BYTES` element from either representation.
     *
     * Byte strings used to be represented as a JSON array holding one entry per byte. That cost a `JsonLiteral`
     * plus a `String` plus its backing array for every single byte - on the order of 80 bytes of heap to carry
     * one byte of payload - so a 230 KB portrait became roughly 460,000 objects and tens of megabytes of tree,
     * per copy. A verifier retaining a handful of such sessions exhausted a 768 MiB heap. They are written as a
     * single base64url string now, which is one object regardless of size.
     *
     * The array form is still accepted, because credential data persisted or sent by existing clients carries
     * it. base64url rather than standard base64: it is what OpenID4VP and ISO 18013-5 use elsewhere, and it
     * survives being placed in a URL without further escaping.
     */
    private fun JsonElement.decodeSchemaBytes(policy: ByteArrayInputPolicy): ByteArray = when (this) {
        is JsonArray -> {
            require(policy == ByteArrayInputPolicy.WARN) {
                "A BYTES element must be sent as a base64url string, but arrived as a JSON array of $size " +
                        "entries. The array form is deprecated: parsing it costs roughly 80 bytes of heap per " +
                        "byte of payload."
            }
            log.warn {
                "A BYTES element arrived as a JSON array of $size entries. Send it as a base64url string: the " +
                        "array form allocates a JSON value per byte, so a 230 KB payload costs tens of " +
                        "megabytes per copy. It is still accepted, and stored data in that form still reads."
            }
            map { it.jsonPrimitive.int.toByte() }.toByteArray()
        }

        else -> jsonPrimitive.content.decodeFromBase64Url()
    }

    fun JsonElement.decodeByScheme(
        schemaType: MdocsSchemaType,
        bytesPolicy: ByteArrayInputPolicy = ByteArrayInputPolicy.WARN,
    ): Any {
        return when (schemaType.type) {
            // Basic types:
            STRING -> jsonPrimitive.content
            INT -> jsonPrimitive.int
            LONG -> jsonPrimitive.long
            UINT -> jsonPrimitive.long.toUInt()
            BOOLEAN -> jsonPrimitive.boolean
            BYTES -> decodeSchemaBytes(bytesPolicy)
            DATE -> LocalDate.parse(jsonPrimitive.content)
            DATETIME -> Instant.parse(jsonPrimitive.content)

            // Primitive types:
            ARRAY -> jsonArray.map { it.decodeByScheme(schemaType.generic!!, bytesPolicy) }
            MAP -> jsonObject.mapValues { (_, value) -> value.decodeByScheme(schemaType.generic!!, bytesPolicy) }
        }
    }

    // JSON -> CBOR
    fun JsonElement.schemafulJsonToCborElement(
        schemaType: MdocsSchemaType,
        bytesPolicy: ByteArrayInputPolicy = ByteArrayInputPolicy.WARN,
    ): CborElement {
        if (this is JsonNull) return CborNull()

        return when (schemaType.type) {
            STRING -> CborString(jsonPrimitive.content)
            INT -> CborInteger(jsonPrimitive.long)
            LONG -> CborInteger(jsonPrimitive.long)
            UINT -> CborInteger(jsonPrimitive.long.toULong())
            BOOLEAN -> CborBoolean(jsonPrimitive.boolean)
            BYTES -> CborByteString(decodeSchemaBytes(bytesPolicy))

            // Applying CBOR tags directly!
            DATE -> CborString(jsonPrimitive.content, 1004u)
            DATETIME -> CborString(jsonPrimitive.content.toMdocTDateString(), 0u)

            ARRAY -> CborArray(jsonArray.map { it.schemafulJsonToCborElement(schemaType.generic!!, bytesPolicy) })
            MAP -> CborMap(
                jsonObject.map { (k, v) ->
                    CborString(k) to v.schemafulJsonToCborElement(schemaType.generic!!, bytesPolicy)
                }.toMap()
            )
        }
    }

    fun JsonElement.jsonToCborElement(): CborElement {
        return when (this) {
            is JsonNull -> CborNull()
            is JsonPrimitive -> when {
                this.isString -> jsonPrimitive.content.toCborElement()
                this.intOrNull != null || this.longOrNull != null -> CborInteger(jsonPrimitive.long)
                this.doubleOrNull != null -> CborFloat(jsonPrimitive.double)
                this.booleanOrNull != null -> CborBoolean(jsonPrimitive.boolean)
                else -> throw NotImplementedError("Unknown json type: '$this' (${this::class.simpleName})")
            }

            is JsonArray -> CborArray(jsonArray.map { it.jsonToCborElement() })

            is JsonObject -> CborMap(
                jsonObject.map { (k, v) ->
                    CborString(k) to v.jsonToCborElement()
                }.toMap()
            )
        }
    }

    // CBOR -> JSON
    fun CborElement.schemafulToJsonElement(schemaType: MdocsSchemaType): JsonElement {
        if (this is CborNull) return JsonNull

        return when (schemaType.type) {
            STRING, DATE, DATETIME -> JsonPrimitive((this as CborString).value)
            INT, LONG -> JsonPrimitive((this as CborInteger).long)
            UINT -> JsonPrimitive((this as CborInteger).absoluteValue.toUInt())
            BOOLEAN -> JsonPrimitive((this as CborBoolean).value)
            BYTES -> JsonPrimitive((this as CborByteString).toByteArray().encodeToBase64Url())
            ARRAY -> JsonArray((this as CborArray).map { it.schemafulToJsonElement(schemaType.generic!!) })
            MAP -> JsonObject((this as CborMap).entries.associate { (k, v) ->
                (k as CborString).value to v.schemafulToJsonElement(schemaType.generic!!)
            })
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    fun Any?.toCborElement(): CborElement = when (this) {
        is String -> {
            runCatching {
                LocalDate.parse(this)
            }.fold(onSuccess = {
                CborString(this, 1004UL)
            }, onFailure = {
                CborString(this)
            })
        }

        is Int -> CborInteger(this.toLong())
        is Long -> CborInteger(this)
        is UInt -> CborInteger(this.toULong())
        is ULong -> CborInteger(this)
        is Boolean -> CborBoolean(this)
        is ByteArray -> CborByteString(this)
        is LocalDate -> CborString(this.toString(), 1004u)
        is Instant -> CborString(this.toMdocTDateString(), 0u)
        is List<*> -> CborArray(this.map { it.toCborElement() })
        is Map<*, *> -> CborMap(this.entries.associate { CborString(it.key as String) to it.value.toCborElement() })
        is CborElement -> this
        is JsonElement -> this.jsonToCborElement()
        null -> CborNull()

        else -> this.toSerializedJsonElement().jsonToCborElement()
        // else -> throw IllegalArgumentException("Cannot convert ${this::class.simpleName} to CborElement")
    }

    fun schemaAwareValueMappingFunction(
        schema: MdocsSchema,
        bytesPolicy: ByteArrayInputPolicy = ByteArrayInputPolicy.WARN,
    ): (docType: String, namespace: String, elementIdentifier: String, elementValueJson: JsonElement) -> CborElement? =
        { docType: String, namespace: String, elementIdentifier: String, value: JsonElement ->

            val schemaType = schema.credentialSchemas[docType]?.get(namespace)?.get(elementIdentifier)
                ?: throw IllegalArgumentException("Element $elementIdentifier not defined in schema")

            value.schemafulJsonToCborElement(schemaType, bytesPolicy)
        }
    /*
            val schemaType =
                schema.credentialSchemas.getOrElse(docType) { throw IllegalArgumentException("Credential with doctype \"$docType\" is not defined in schema") }
                    .getOrElse(namespace) { throw IllegalArgumentException("Unknown namespace \"$namespace\" in credential \"${docType}\" for mdocs schema") }
                    .getOrElse(elementIdentifier) { throw IllegalArgumentException("Element \"$elementIdentifier\" is not defined in schema for namespace \"$namespace\" of credential \"${docType}\"") }

            runCatching { value.decodeByMdocsScheme(schemaType) }.getOrElse { ex ->
                throw IllegalArgumentException(
                    "Failed to decode element \"$elementIdentifier\" in namespace \"$namespace\" of credential \"${docType}\": $value could not be decoded as $schemaType: ${ex.message}",
                    ex
                )
            }
        }*/
}
