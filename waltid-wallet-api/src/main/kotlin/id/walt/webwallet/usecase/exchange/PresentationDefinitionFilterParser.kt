package id.walt.webwallet.usecase.exchange

import id.walt.oid4vc.data.dif.InputDescriptorConstraints
import id.walt.oid4vc.data.dif.InputDescriptorField
import id.walt.oid4vc.data.dif.InputDescriptorSchema
import id.walt.oid4vc.data.dif.PresentationDefinition
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonPrimitive

class PresentationDefinitionFilterParser {
    fun parse(presentationDefinition: PresentationDefinition): List<List<TypeFilter>> =
        presentationDefinition.inputDescriptors.mapNotNull { inputDescriptor ->
            getFilter(inputDescriptor.constraints)?.plus(getFilter(inputDescriptor.schema) ?: listOf())
        }

    private fun getFilter(inputDescriptor: InputDescriptorConstraints?) =
        inputDescriptor?.fields?.map { createTypeFilter(it) }

    private fun getFilter(schemas: List<InputDescriptorSchema>?) = schemas?.map { schema -> createTypeFilter(schema) }

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
data class TypeFilter(val path: List<String>, val type: String? = null, val pattern: String)