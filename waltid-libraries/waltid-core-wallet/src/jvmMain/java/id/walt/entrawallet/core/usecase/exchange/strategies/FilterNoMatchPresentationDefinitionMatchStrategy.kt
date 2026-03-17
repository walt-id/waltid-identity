package id.walt.corewallet.usecase.exchange.strategies

import id.walt.corewallet.utils.WalletCredential
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.corewallet.usecase.exchange.FilterData
import id.walt.corewallet.usecase.exchange.PresentationDefinitionFilterParser

class FilterNoMatchPresentationDefinitionMatchStrategy(
    private val filterParser: PresentationDefinitionFilterParser,
) : BaseFilterPresentationDefinitionMatchStrategy<List<FilterData>>() {

    override fun match(
        credentials: List<WalletCredential>, presentationDefinition: PresentationDefinition,
    ): List<FilterData> = match(credentials, filterParser.parse(presentationDefinition))

    private fun match(
        credentialList: List<WalletCredential>, filters: List<FilterData>,
    ) = filters.filter { fields ->
        credentialList.none { credential ->
            isMatching(credential, fields.filters)
        }
    }.distinct()
}
