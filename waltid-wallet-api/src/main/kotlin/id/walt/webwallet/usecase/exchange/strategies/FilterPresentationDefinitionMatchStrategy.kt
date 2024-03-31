package id.walt.webwallet.usecase.exchange.strategies

import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.usecase.exchange.PresentationDefinitionFilterParser
import id.walt.webwallet.usecase.exchange.TypeFilter

class FilterPresentationDefinitionMatchStrategy(
    private val filterParser: PresentationDefinitionFilterParser,
) : BaseFilterPresentationDefinitionMatchStrategy<List<WalletCredential>>() {

    override fun match(
        credentials: List<WalletCredential>, presentationDefinition: PresentationDefinition
    ): List<WalletCredential> = match(credentials, filterParser.parse(presentationDefinition))

    private fun match(
        credentialList: List<WalletCredential>, filters: List<List<TypeFilter>>
    ) = credentialList.filter { credential ->
        filters.any { fields ->
            isMatching(credential, fields)
        }
    }
}