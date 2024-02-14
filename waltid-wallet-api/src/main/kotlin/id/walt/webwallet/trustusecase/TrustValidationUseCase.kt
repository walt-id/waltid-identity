package id.walt.webwallet.trustusecase

import id.walt.webwallet.db.models.WalletCredential

interface TrustValidationUseCase {
    suspend fun status(credential: WalletCredential): TrustStatus
}

enum class TrustStatus {
    Trusted, Untrusted, Unverified,
}