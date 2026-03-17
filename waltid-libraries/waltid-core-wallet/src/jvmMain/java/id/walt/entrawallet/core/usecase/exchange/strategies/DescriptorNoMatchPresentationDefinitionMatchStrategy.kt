package id.walt.corewallet.usecase.exchange.strategies

import id.walt.corewallet.utils.WalletCredential
import id.walt.oid4vc.data.dif.InputDescriptor
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.corewallet.usecase.exchange.FilterData
import id.walt.corewallet.usecase.exchange.TypeFilter
import id.walt.corewallet.utils.JsonUtils
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

class DescriptorNoMatchPresentationDefinitionMatchStrategy : PresentationDefinitionMatchStrategy<List<FilterData>> {
    override fun match(
        credentials: List<WalletCredential>, presentationDefinition: PresentationDefinition,
    ): List<FilterData> = match(credentials, presentationDefinition.inputDescriptors)

    private fun match(
        credentialList: List<WalletCredential>, inputDescriptors: List<InputDescriptor>,
    ) = inputDescriptors.filter { desc ->
        credentialList.none { cred ->
            desc.name == JsonUtils.tryGetData(cred.parsedDocument, "type")?.jsonArray?.last()?.jsonPrimitive?.content
                    || desc.id == JsonUtils.tryGetData(
                cred.parsedDocument,
                "type"
            )?.jsonArray?.last()?.jsonPrimitive?.content
        }
    }.map {
        FilterData(
            credential = it.name ?: it.id,
            filters = listOf(TypeFilter(path = emptyList(), pattern = it.name ?: it.id))
        )
    }
}
