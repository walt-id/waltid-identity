package id.walt.webwallet.usecase.exchange

import id.walt.oid4vc.data.dif.PresentationDefinition
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonPrimitive

class PresentationDefinitionFilterParser {
    fun parse(presentationDefinition: PresentationDefinition): List<List<TypeFilter>> =
        presentationDefinition.inputDescriptors.mapNotNull { inputDescriptor ->
            inputDescriptor.constraints?.fields?.filter { field -> field.path.any { path -> path.contains("type") } }
                ?.map {
                    val path = it.path.first().removePrefix("$.")
                    val filterType = it.filter?.get("type")?.jsonPrimitive?.content
                    val filterPattern =
                        it.filter?.get("pattern")?.jsonPrimitive?.content ?: throw IllegalArgumentException(
                            "No filter pattern in presentation definition constraint"
                        )

                    TypeFilter(path, filterType, filterPattern)
                }?.plus(inputDescriptor.schema?.map { schema -> TypeFilter("type", "string", schema.uri) } ?: listOf())
        }
}

@Serializable
data class TypeFilter(val path: String, val type: String? = null, val pattern: String)