package id.walt.openid4vci.proofs

import kotlinx.serialization.Serializable

interface CredentialNonceService {
    suspend fun issue(binding: CredentialNonceBinding): IssuedCredentialNonce

    suspend fun validate(
        nonce: String,
        binding: CredentialNonceBinding,
    ): CredentialNonceValidationResult
}

@Serializable
data class CredentialNonceBinding(
    val credentialIssuer: String,
    val credentialEndpoint: String,
    val nonceEndpoint: String,
) {
    init {
        require(credentialIssuer.isNotBlank()) { "credentialIssuer must not be blank" }
        require(credentialEndpoint.isNotBlank()) { "credentialEndpoint must not be blank" }
        require(nonceEndpoint.isNotBlank()) { "nonceEndpoint must not be blank" }
    }
}

data class IssuedCredentialNonce(
    val nonce: String,
    val expiresInSeconds: Long,
)

data class CredentialNonceValidationContext(
    val service: CredentialNonceService,
    val binding: CredentialNonceBinding,
)

enum class CredentialNonceValidationResult {
    VALID,
    INVALID,
}
