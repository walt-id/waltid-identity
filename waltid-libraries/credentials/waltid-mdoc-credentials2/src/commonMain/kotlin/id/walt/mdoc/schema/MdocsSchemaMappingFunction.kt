package id.walt.mdoc.schema

import id.walt.mdoc.schema.MdocsSchema.MdocsDatatype.*
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.*
import kotlin.time.Instant

object MdocsSchemaMappingFunction {

    fun JsonElement.decodeByMdocsScheme(schemaType: MdocsSchema.MdocsSchemaType): Any {
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
            ARRAY -> jsonArray.map { it.decodeByMdocsScheme(schemaType.generic!!) }
            MAP -> jsonObject.mapValues { (_, value) -> value.decodeByMdocsScheme(schemaType.generic!!) }
        }
    }

    fun schemaAwareValueMappingFunction(schema: MdocsSchema): (docType: String, namespace: String, elementIdentifier: String, elementValueJson: JsonElement) -> Any? =
        { docType: String, namespace: String, elementIdentifier: String, value: JsonElement ->

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
        }
}
