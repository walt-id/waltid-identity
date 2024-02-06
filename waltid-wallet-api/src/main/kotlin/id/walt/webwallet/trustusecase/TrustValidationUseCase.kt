package id.walt.webwallet.trustusecase

interface TrustValidationUseCase {
    suspend fun status(did: String, type: String, isIssuer: Boolean): TrustStatus
}

enum class TrustStatus {
    Trusted, Untrusted, NotFound,
}