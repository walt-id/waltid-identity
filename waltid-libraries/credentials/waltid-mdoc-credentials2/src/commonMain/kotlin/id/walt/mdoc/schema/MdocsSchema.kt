@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.mdoc.schema

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class MdocsSchema(
    val credentialSchemas: DocTypeToNamespaces
) {

    enum class MdocsDatatype(val isGeneric: Boolean = false) {
        // Basic types:
        STRING,
        BYTES,
        INT,
        LONG,
        UINT,
        BOOLEAN,
        DATE,
        DATETIME,

        // Generic types:
        ARRAY(true),
        MAP(true);

        companion object {
            fun parseFromString(string: String): MdocsDatatype =
                MdocsDatatype.valueOf(string.trim().uppercase().replace("-", ""))
        }

        fun asSchemaType() = MdocsSchemaType(this)
    }

    /**
     * @param type Main type
     * @param generic Specific type for certain main types
     */
    @Serializable
    data class MdocsSchemaType(val type: MdocsDatatype, val generic: MdocsSchemaType? = null) {

        override fun toString() =
            "$type${if (generic != null) "<$generic>" else ""}"

        constructor(type: MdocsDatatype, generic: MdocsDatatype) : this(
            type = type,
            generic = MdocsSchemaType(generic)
                .apply {
                    require(!generic.isGeneric) { "You have (in code) supplied a generic type without specifying the generic: Generic $generic for type $type is missing the generic" }
                }
        )

        companion object {
            fun parseFromString(string: String): MdocsSchemaType {
                val string = string.trim()
                val hasGenerics = string.contains(">")
                if (hasGenerics) {
                    val types = string.split(">")

                    val mainType = MdocsDatatype.parseFromString(types.first())
                    if (!mainType.isGeneric) throw IllegalArgumentException("Provided generics for type that does not support generics: $mainType in string: $string")
                    val genericType = parseFromString(types.drop(1).joinToString(">"))

                    return MdocsSchemaType(
                        type = mainType,
                        generic = genericType
                    )
                }

                return MdocsSchemaType(MdocsDatatype.parseFromString(string))
            }
        }
    }

    typealias AttributeToType = Map<String, MdocsSchemaType>
    typealias NamespaceToAttribute = Map<String, AttributeToType>
    typealias DocTypeToNamespaces = Map<String, NamespaceToAttribute>

    companion object {
        fun parseFromJson(jsonObject: JsonObject): MdocsSchema = MdocsSchema(
            jsonObject.mapValues { (_, namespaces) ->
                namespaces.jsonObject.mapValues { (_, element) ->
                    element.jsonObject.mapValues { (_, elementValue) ->
                        val schemaType = MdocsSchemaType.parseFromString(elementValue.jsonPrimitive.content)
                        schemaType
                    }
                }
            })
    }

    override fun toString(): String {
        val sb = StringBuilder("MdocsSchema{\n")
        credentialSchemas.forEach { (credential, namespaces) ->
            sb.appendLine("|  $credential: (credential doctype)")
            namespaces.forEach { (namespace, element) ->
                sb.appendLine("|    $namespace: (namespace)")
                element.forEach { (elementIdentifier, elementValue) ->
                    sb.appendLine("|      $elementIdentifier => $elementValue (element)")
                }
            }
        }
        sb.appendLine("}")
        return sb.toString()
    }
}
