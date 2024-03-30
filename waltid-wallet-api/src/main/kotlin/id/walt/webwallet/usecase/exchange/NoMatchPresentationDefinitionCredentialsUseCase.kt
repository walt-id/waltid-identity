package id.walt.webwallet.usecase.exchange

import id.walt.oid4vc.data.dif.PresentationDefinition
import kotlinx.uuid.UUID

class NoMatchPresentationDefinitionCredentialsUseCase {
    fun find(wallet: UUID, presentationDefinition: PresentationDefinition): List<List<TypeFilter>> {
        TODO()
    }
}