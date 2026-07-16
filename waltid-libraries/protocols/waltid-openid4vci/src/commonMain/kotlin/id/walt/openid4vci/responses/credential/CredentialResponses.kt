package id.walt.openid4vci.responses.credential

import id.walt.openid4vci.errors.OAuthError
import id.walt.openid4vci.requests.credential.encryption.CredentialEncryptionProfile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/**
 * Credential response per OpenID4VCI credential endpoint.
 * Fields match the specification response body.
 */
@Serializable
data class CredentialResponse(
    val credentials: List<IssuedCredential>? = null,
    @SerialName("transaction_id") val transactionId: String? = null,
    val interval: Long? = null,
    @SerialName("notification_id") val notificationId: String? = null,
)

@Serializable
data class IssuedCredential(
    val credential: JsonElement,
)

data class CredentialResponseHttp(
    val status: Int,
    val payload: Map<String, JsonElement>,
    val headers: Map<String, String> = emptyMap(),
)

sealed class CredentialResponseResult {
    data class Success(
        val response: CredentialResponse,
    ) : CredentialResponseResult()

    data class Failure(val error: OAuthError) : CredentialResponseResult()

    fun isSuccess(): Boolean = this is Success
}
