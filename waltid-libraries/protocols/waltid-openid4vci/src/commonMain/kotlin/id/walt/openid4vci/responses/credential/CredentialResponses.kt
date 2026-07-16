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

fun CredentialResponse.toJsonObject(): JsonObject = buildJsonObject {
    credentials?.let { issued ->
        put(
            "credentials",
            buildJsonArray {
                issued.forEach { credentialEntry ->
                    add(
                        JsonObject(
                            mapOf("credential" to credentialEntry.credential)
                        )
                    )
                }
            }
        )
    }
    transactionId?.let { put("transaction_id", JsonPrimitive(it)) }
    interval?.let { put("interval", JsonPrimitive(it)) }
    notificationId?.let { put("notification_id", JsonPrimitive(it)) }
}

sealed interface CredentialResponseBody {
    data class Json(val payload: Map<String, JsonElement>) : CredentialResponseBody
    data class EncryptedJwt(
        val value: String,
        val contentType: String = CredentialEncryptionProfile.MEDIA_TYPE_JWT,
    ) : CredentialResponseBody
}

data class CredentialResponseHttp(
    val status: Int,
    val body: CredentialResponseBody,
    val headers: Map<String, String> = emptyMap(),
) {
    constructor(
        status: Int,
        payload: Map<String, JsonElement>,
        headers: Map<String, String> = emptyMap(),
    ) : this(status, CredentialResponseBody.Json(payload), headers)

    val payload: Map<String, JsonElement>
        get() = when (body) {
            is CredentialResponseBody.Json -> body.payload
            is CredentialResponseBody.EncryptedJwt -> emptyMap()
        }

    val encryptedJwt: String?
        get() = (body as? CredentialResponseBody.EncryptedJwt)?.value

    val contentType: String?
        get() = (body as? CredentialResponseBody.EncryptedJwt)?.contentType
}

sealed class CredentialResponseResult {
    data class Success(
        val response: CredentialResponse,
    ) : CredentialResponseResult()

    data class Failure(val error: OAuthError) : CredentialResponseResult()

    fun isSuccess(): Boolean = this is Success
}
