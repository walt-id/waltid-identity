package id.walt.openid4vci.responses.credential

import id.walt.openid4vci.errors.OAuthError
import kotlinx.serialization.json.JsonElement

/**
 * Credential response per OpenID4VCI credential endpoint.
 * Fields match the specification response body.
 */
data class CredentialResponse(
    val credentials: List<IssuedCredential>? = null,
    val transactionId: String? = null,
    val interval: Long? = null,
    val notificationId: String? = null,
)

data class IssuedCredential(
    val credential: JsonElement,
)

sealed class CredentialResponseResult {
    data class Success(
        val response: CredentialResponse,
    ) : CredentialResponseResult()

    data class Failure(val error: OAuthError) : CredentialResponseResult()

    fun isSuccess(): Boolean = this is Success
}
