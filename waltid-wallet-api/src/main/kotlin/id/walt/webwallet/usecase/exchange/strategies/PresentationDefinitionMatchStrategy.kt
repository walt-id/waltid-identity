package id.walt.webwallet.usecase.exchange.strategies

import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.webwallet.db.models.WalletCredential

interface PresentationDefinitionMatchStrategy<out T> {
    fun match(
        credentials: List<WalletCredential>, presentationDefinition: PresentationDefinition
    ): T
}