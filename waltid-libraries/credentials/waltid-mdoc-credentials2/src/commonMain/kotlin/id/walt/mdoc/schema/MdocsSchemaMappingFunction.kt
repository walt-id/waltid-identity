@file:OptIn(ExperimentalSerializationApi::class, ExperimentalUnsignedTypes::class)

package id.walt.mdoc.schema

import id.walt.crypto.utils.JsonUtils.toSerializedJsonElement
import id.walt.mdoc.schema.MdocsSchema.MdocsDatatype.*
import id.walt.mdoc.schema.MdocsSchema.MdocsSchemaType
import kotlinx.datetime.LocalDate
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.*
import kotlinx.serialization.json.*
import kotlin.time.Instant

object MdocsSchemaMappingFunction {

    fun JsonElement.decodeByScheme(schemaType: MdocsSchemaType): Any {
        return when (schemaType.type) {
            // Basic types:
            STRING -> jsonPrimitive.content
            INT -> jsonPrimitive.int
            LONG -> jsonPrimitive.long
            UINT -> jsonPrimitive.long.toUInt()
            BOOLEAN -> jsonPrimitive.boolean
            BYTES -> jsonArray.map { it.jsonPrimitive.int.toByte() }.toByteArray()
            DATE -> LocalDate.parse(jsonPrimitive.content)
            DATETIME -> Instant.parse(jsonPrimitive.content)

            // Primitive types:
            ARRAY -> jsonArray.map { it.decodeByScheme(schemaType.generic!!) }
            MAP -> jsonObject.mapValues { (_, value) -> value.decodeByScheme(schemaType.generic!!) }
        }
    }

    // JSON -> CBOR
    fun JsonElement.schemafulJsonToCborElement(schemaType: MdocsSchemaType): CborElement {
        if (this is JsonNull) return CborNull()

        return when (schemaType.type) {
            STRING -> CborString(jsonPrimitive.content)
            INT -> CborInteger(jsonPrimitive.long)
            LONG -> CborInteger(jsonPrimitive.long)
            UINT -> CborInteger(jsonPrimitive.long.toULong())
            BOOLEAN -> CborBoolean(jsonPrimitive.boolean)
            BYTES -> CborByteString(jsonArray.map { it.jsonPrimitive.int.toByte() }.toByteArray())

            // Applying CBOR tags directly!
            DATE -> CborString(jsonPrimitive.content, 1004u)
            DATETIME -> CborString(jsonPrimitive.content, 0u)

            ARRAY -> CborArray(jsonArray.map { it.schemafulJsonToCborElement(schemaType.generic!!) })
            MAP -> CborMap(
                jsonObject.map { (k, v) ->
                    CborString(k) to v.schemafulJsonToCborElement(schemaType.generic!!)
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
            UINT -> JsonPrimitive((this as CborInteger).value.toLong())
            BOOLEAN -> JsonPrimitive((this as CborBoolean).value)
            BYTES -> JsonArray((this as CborByteString).value.map { JsonPrimitive(it) })
            ARRAY -> JsonArray((this as CborArray).map { it.schemafulToJsonElement(schemaType.generic!!) })
            MAP -> JsonObject((this as CborMap).entries.associate { (k, v) ->
                (k as CborString).value to v.schemafulToJsonElement(schemaType.generic!!)
            })
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    fun Any.toCborElement(): CborElement = when (this) {
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
        is Instant -> CborString(this.toString(), 0u)
        is List<*> -> CborArray(this.map { it!!.toCborElement() })
        is Map<*, *> -> CborMap(this.entries.associate { CborString(it.key as String) to it.value!!.toCborElement() })
        is CborElement -> this
        is JsonElement -> this.jsonToCborElement()

        else -> this.toSerializedJsonElement().jsonToCborElement()
        // else -> throw IllegalArgumentException("Cannot convert ${this::class.simpleName} to CborElement")
    }

    fun schemaAwareValueMappingFunction(schema: MdocsSchema): (docType: String, namespace: String, elementIdentifier: String, elementValueJson: JsonElement) -> CborElement? =
        { docType: String, namespace: String, elementIdentifier: String, value: JsonElement ->

            val schemaType = schema.credentialSchemas[docType]?.get(namespace)?.get(elementIdentifier)
                ?: throw IllegalArgumentException("Element $elementIdentifier not defined in schema")

            value.schemafulJsonToCborElement(schemaType)
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
