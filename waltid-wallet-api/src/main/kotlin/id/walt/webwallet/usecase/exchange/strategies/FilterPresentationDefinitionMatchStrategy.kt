package id.walt.webwallet.usecase.exchange.strategies

import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.usecase.exchange.FilterData
import id.walt.webwallet.usecase.exchange.PresentationDefinitionFilterParser

class FilterPresentationDefinitionMatchStrategy(
    private val filterParser: PresentationDefinitionFilterParser,
) : BaseFilterPresentationDefinitionMatchStrategy<List<WalletCredential>>() {

    override fun match(
        credentials: List<WalletCredential>, presentationDefinition: PresentationDefinition
    ): List<WalletCredential> = match(credentials, filterParser.parse(presentationDefinition))

    private fun match(
        credentialList: List<WalletCredential>, filters: List<FilterData>
    ) = credentialList.filter { credential ->
        filters.isNotEmpty() && filters.all { fields ->
            isMatching(credential, fields.filters)
        }
    }
}