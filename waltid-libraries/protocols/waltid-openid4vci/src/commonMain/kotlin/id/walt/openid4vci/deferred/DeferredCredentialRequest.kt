package id.walt.openid4vci.deferred

import id.walt.openid4vci.errors.CredentialError
import id.walt.openid4vci.errors.OAuthError
import id.walt.openid4vci.requests.credential.encryption.CredentialResponseEncryptionParameters
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeferredCredentialRequest(
    @SerialName("transaction_id") val transactionId: String,
    @SerialName("credential_response_encryption") val credentialResponseEncryption: CredentialResponseEncryptionParameters? = null,
)

sealed interface DeferredCredentialRequestResult {
    data class Success(val request: DeferredCredentialRequest) : DeferredCredentialRequestResult
    data class Failure(val error: CredentialError) : DeferredCredentialRequestResult
    data class OAuthFailure(val error: OAuthError) : DeferredCredentialRequestResult
}

@Serializable
enum class DeferredStatus { PENDING, READY, DENIED }

@Serializable
data class DeferredIssuanceConfig(
    val pollingIntervalSeconds: Long = 30,
    val lifetimeSeconds: Long = 7 * 24 * 60 * 60,
) {
    init {
        require(pollingIntervalSeconds > 0) { "Deferred polling interval must be positive" }
        require(lifetimeSeconds > 0) { "Deferred lifetime must be positive" }
    }
}
