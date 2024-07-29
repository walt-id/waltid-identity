package id.walt.cli.presexch.strategies

import id.walt.cli.models.Credential
import id.walt.oid4vc.data.dif.InputDescriptor
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.cli.util.JsonUtils
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

class DescriptorPresentationDefinitionMatchStrategy : PresentationDefinitionMatchStrategy<List<Credential>> {
    override fun match(
        credentials: List<Credential>, presentationDefinition: PresentationDefinition,
    ): List<Credential> = match(credentials, presentationDefinition.inputDescriptors)

    private fun match(
        credentialList: List<Credential>, inputDescriptors: List<InputDescriptor>,
    ) = credentialList.filter { cred ->
        inputDescriptors.any { desc ->
            desc.name == JsonUtils.tryGetData(cred.parsedDocument, "type")?.jsonArray?.last()?.jsonPrimitive?.content
        }
    }
}