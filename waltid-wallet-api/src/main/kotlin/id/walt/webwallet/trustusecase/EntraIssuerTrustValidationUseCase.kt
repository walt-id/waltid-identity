package id.walt.webwallet.trustusecase

import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.seeker.Seeker
import id.walt.webwallet.service.trust.TrustValidationService

class EntraIssuerTrustValidationUseCase(
    private val issuerTrustValidationService: TrustValidationService,
    private val didSeeker: Seeker<String>,
    private val credentialTypeSeeker: Seeker<String>,
) : TrustValidationUseCase {

    override suspend fun status(credential: WalletCredential, checkIssuer: Boolean): TrustStatus =
        when (issuerTrustValidationService.validate(didSeeker.get(credential), credentialTypeSeeker.get(credential))) {
            true -> TrustStatus.Trusted
            false -> TrustStatus.Untrusted
        }
}