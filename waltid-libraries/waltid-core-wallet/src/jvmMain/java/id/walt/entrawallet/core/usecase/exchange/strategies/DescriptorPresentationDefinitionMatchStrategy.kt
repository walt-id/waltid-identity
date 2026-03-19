package id.walt.corewallet.usecase.exchange.strategies

import id.walt.corewallet.utils.WalletCredential
import id.walt.oid4vc.data.dif.InputDescriptor
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.corewallet.utils.JsonUtils
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

class DescriptorPresentationDefinitionMatchStrategy : PresentationDefinitionMatchStrategy<List<WalletCredential>> {
    override fun match(
        credentials: List<WalletCredential>, presentationDefinition: PresentationDefinition,
    ): List<WalletCredential> = match(credentials, presentationDefinition.inputDescriptors)

    private fun match(
        credentialList: List<WalletCredential>, inputDescriptors: List<InputDescriptor>,
    ) = credentialList.filter { cred ->
        inputDescriptors.any { desc ->
            desc.name == JsonUtils.tryGetData(cred.parsedDocument, "type")?.jsonArray?.last()?.jsonPrimitive?.content
        }
    }
}
