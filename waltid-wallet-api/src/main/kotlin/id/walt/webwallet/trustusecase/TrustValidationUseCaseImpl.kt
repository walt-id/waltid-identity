package id.walt.webwallet.trustusecase

import id.walt.webwallet.service.trust.TrustValidationService

class TrustValidationUseCaseImpl(
    private val issuerTrustValidationService: TrustValidationService,
    private val verifierTrustValidationService: TrustValidationService,
) : TrustValidationUseCase {
    override suspend fun status(did: String, type: String, isIssuer: Boolean): TrustStatus = let {
        isIssuer.takeIf { it }?.let {
            issuerTrustValidationService
        } ?: verifierTrustValidationService
    }.let {
        when (it.validate(did, type)) {
            true -> TrustStatus.Trusted
            false -> TrustStatus.Untrusted
        }
    }
}