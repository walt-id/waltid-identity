package id.walt.webwallet.usecase.trust

import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.seeker.Seeker
import id.walt.webwallet.service.trust.TrustValidationService

class TrustValidationUseCaseImpl(
    private val trustValidationService: TrustValidationService,
    private val didSeeker: Seeker<String>,
    private val credentialTypeSeeker: Seeker<String>,
) : TrustValidationUseCase {

    override suspend fun status(credential: WalletCredential): TrustStatus = runCatching {
        trustValidationService.validate(didSeeker.get(credential), credentialTypeSeeker.get(credential))
    }.fold(onSuccess = {
        when (it) {
            true -> TrustStatus.Trusted
            false -> TrustStatus.Untrusted
        }
    }, onFailure = {
        TrustStatus.Untrusted
    })
}