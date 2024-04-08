package id.walt.webwallet.usecase.exchange.strategies

import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.usecase.exchange.PresentationDefinitionFilterParser
import id.walt.webwallet.usecase.exchange.TypeFilter

class FilterNoMatchPresentationDefinitionMatchStrategy(
    private val filterParser: PresentationDefinitionFilterParser,
) : BaseFilterPresentationDefinitionMatchStrategy<List<TypeFilter>>() {

    override fun match(
        credentials: List<WalletCredential>, presentationDefinition: PresentationDefinition
    ): List<TypeFilter> = match(credentials, filterParser.parse(presentationDefinition))

    private fun match(
        credentialList: List<WalletCredential>, filters: List<List<TypeFilter>>
    ) = filters.filter { fields ->
        credentialList.none { credential ->
            isMatching(credential, fields)
        }
    }.flatten().distinct()
}