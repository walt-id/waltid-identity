package id.walt.webwallet.usecase.issuer

import id.walt.webwallet.service.issuers.IssuersService
import kotlinx.uuid.UUID

class IssuerNameResolutionUseCase(
    private val service: IssuersService,
    private val issuerUseCase: IssuerUseCase,
) {
    fun resolve(wallet: UUID, did: String): String = let {
//        issuerUseCase.get()
        ""
    }
}