package id.walt.corewallet.usecase.exchange.strategies

import id.walt.corewallet.utils.WalletCredential
import id.walt.oid4vc.data.dif.PresentationDefinition

interface PresentationDefinitionMatchStrategy<out T> {
    fun match(
        credentials: List<WalletCredential>, presentationDefinition: PresentationDefinition,
    ): T
}
