package id.walt.webwallet.usecase.exchange

import id.walt.oid4vc.data.dif.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonPrimitive

class PresentationDefinitionFilterParser {
    fun parse(presentationDefinition: PresentationDefinition): List<FilterData> =
        presentationDefinition.inputDescriptors.map { inputDescriptor ->
            FilterData(
                credential = inputDescriptor.name ?: inputDescriptor.id,
                filters = getFilters(inputDescriptor)
            )
        }

    private fun getFilters(inputDescriptor: InputDescriptor) =
        getFilter(inputDescriptor.constraints) + getFilter(inputDescriptor.schema)

    private fun getFilter(inputDescriptor: InputDescriptorConstraints?) =
        inputDescriptor?.fields?.map { createTypeFilter(it) } ?: emptyList()

    private fun getFilter(schemas: List<InputDescriptorSchema>?) =
        schemas?.map { schema -> createTypeFilter(schema) } ?: emptyList()

    private fun createTypeFilter(inputDescriptorField: InputDescriptorField) = let {
        val paths = inputDescriptorField.path.map { it.removePrefix("$.") }
        val filterType = inputDescriptorField.filter?.get("type")?.jsonPrimitive?.content
        val filterPattern = inputDescriptorField.filter?.get("pattern")?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("No filter pattern in presentation definition constraint")
        TypeFilter(paths, filterType, filterPattern)
    }

    private fun createTypeFilter(inputDescriptorSchema: InputDescriptorSchema) =
        TypeFilter(listOf("type"), "string", inputDescriptorSchema.uri)
}

@Serializable
data class FilterData(
    val credential: String,
    val filters: List<TypeFilter>,
)

@Serializable
data class TypeFilter(val path: List<String>, val type: String? = null, val pattern: String)