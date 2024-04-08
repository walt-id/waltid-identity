package id.walt.webwallet.usecase.exchange.strategies

import id.walt.oid4vc.data.dif.InputDescriptor
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.usecase.exchange.TypeFilter
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

class DescriptorNoMatchPresentationDefinitionMatchStrategy : PresentationDefinitionMatchStrategy<List<TypeFilter>> {
    override fun match(
        credentials: List<WalletCredential>, presentationDefinition: PresentationDefinition
    ): List<TypeFilter> = match(credentials, presentationDefinition.inputDescriptors)

    private fun match(
        credentialList: List<WalletCredential>, inputDescriptors: List<InputDescriptor>
    ) = inputDescriptors.filter { desc ->
        credentialList.none { cred ->
            desc.name == cred.parsedDocument?.get("type")?.jsonArray?.last()?.jsonPrimitive?.content
                    || desc.id == cred.parsedDocument?.get("type")?.jsonArray?.last()?.jsonPrimitive?.content
        }
    }.map {
        TypeFilter(path = "", pattern = it.name ?: it.id)
    }
}