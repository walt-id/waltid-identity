package id.walt.corewallet.usecase.exchange.strategies

import id.walt.corewallet.utils.WalletCredential
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.corewallet.usecase.exchange.FilterData
import id.walt.corewallet.usecase.exchange.PresentationDefinitionFilterParser

class FilterPresentationDefinitionMatchStrategy(
    private val filterParser: PresentationDefinitionFilterParser,
) : BaseFilterPresentationDefinitionMatchStrategy<List<WalletCredential>>() {

    override fun match(
        credentials: List<WalletCredential>, presentationDefinition: PresentationDefinition,
    ): List<WalletCredential> = match(credentials, filterParser.parse(presentationDefinition))

    private fun match(
        credentialList: List<WalletCredential>, filters: List<FilterData>,
    ) = filters.isNotEmpty().takeIf { it }?.let {
        credentialList.filter { credential ->
            filters.any { fields ->
                isMatching(credential, fields.filters)
            }
        }
    } ?: emptyList()
}
