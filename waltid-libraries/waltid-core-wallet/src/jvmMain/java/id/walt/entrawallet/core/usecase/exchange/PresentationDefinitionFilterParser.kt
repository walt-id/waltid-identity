package id.walt.webwallet.usecase.exchange

import id.walt.oid4vc.data.dif.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonPrimitive

class PresentationDefinitionFilterParser {
    private val prefixRegex = "^(\\$\\.(vc\\.)?)".toRegex()
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
        inputDescriptor?.fields?.mapNotNull { createTypeFilter(it) } ?: emptyList()

    private fun getFilter(schemas: List<InputDescriptorSchema>?) =
        schemas?.map { schema -> createTypeFilter(schema) } ?: emptyList()

    // TODO: Don't just match on the types
    private fun createTypeFilter(inputDescriptorField: InputDescriptorField): TypeFilter? = let {
        val paths = inputDescriptorField.path.map { prefixRegex.replace(it, "") }
        val filterType = inputDescriptorField.filter?.get("type")?.jsonPrimitive?.content

        val filterPattern = inputDescriptorField.filter?.get("const")?.jsonPrimitive?.content
            ?: inputDescriptorField.filter?.get("pattern")?.jsonPrimitive?.content
                ?.removePrefix("^")
                ?.removeSuffix("$")

        /*val filterPattern = inputDescriptorField.filter?.get("pattern")?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("No filter pattern in presentation definition constraint")*/

        filterPattern?.let {
            TypeFilter(paths, filterType, filterPattern)
        }
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
