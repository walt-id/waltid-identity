package id.walt.openid4vci.proofs

import kotlinx.serialization.Serializable

/**
 * Nonces must be bound to all supplied issuer/endpoints and have a bounded lifetime.
 * Validation must not consume a nonce: proof verification may run more than once.
 */
interface CredentialNonceService {
    suspend fun issue(binding: CredentialNonceBinding): IssuedCredentialNonce

    /** Return INVALID for rejected wallet input; throw for issuer operational failures. */
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
)

data class CredentialNonceValidationContext(
    val service: CredentialNonceService,
    val binding: CredentialNonceBinding,
)

enum class CredentialNonceValidationResult {
    VALID,
    INVALID,
}
