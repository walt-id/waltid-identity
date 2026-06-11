package id.walt.openid4vci.responses.token

import id.walt.openid4vci.core.TOKEN_TYPE_BEARER
import id.walt.openid4vci.errors.OAuthError
import id.walt.openid4vci.requests.authorization.AuthorizationDetail
import id.walt.openid4vci.requests.token.AccessTokenRequest
import kotlinx.serialization.json.add
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

data class AccessTokenResponse(
    val tokenType: String = TOKEN_TYPE_BEARER,
    val accessToken: String,
    val expiresIn: Long? = null,
    val extra: Map<String, Any?> = emptyMap(),
)

data class TokenResponseOptions(
    val authorizationDetails: List<AuthorizationDetail> = emptyList(),
    val authorizationDetailsResolver: (suspend (AccessTokenRequest) -> List<AuthorizationDetail>)? = null,
) {
    suspend fun resolveAuthorizationDetails(request: AccessTokenRequest): List<AuthorizationDetail> =
        authorizationDetailsResolver?.invoke(request) ?: authorizationDetails
}

fun AccessTokenResponse.withAuthorizationDetails(
    authorizationDetails: List<AuthorizationDetail>,
): AccessTokenResponse {
    if (authorizationDetails.isEmpty()) {
        return this
    }

    val authorizationDetailsJson = buildJsonArray {
        authorizationDetails.forEach { detail ->
            add(
                buildJsonObject {
                    put("type", detail.type)
                    put("credential_configuration_id", detail.credentialConfigurationId)
                    detail.credentialIdentifiers?.let { credentialIdentifiers ->
                        putJsonArray("credential_identifiers") {
                            credentialIdentifiers.forEach { add(it) }
                        }
                    }
                    detail.claims?.let { put("claims", it) }
                }
            )
        }
    }

    return copy(extra = extra + ("authorization_details" to authorizationDetailsJson))
}

suspend fun AccessTokenResponse.withOptions(
    options: TokenResponseOptions,
    request: AccessTokenRequest,
): AccessTokenResponse =
    withAuthorizationDetails(options.resolveAuthorizationDetails(request))

sealed class AccessTokenResponseResult {
    data class Success(
        val request: AccessTokenRequest,
        val response: AccessTokenResponse,
    ) : AccessTokenResponseResult()
    data class Failure(val error: OAuthError) : AccessTokenResponseResult()

    fun isSuccess(): Boolean = this is Success
}

data class AccessTokenResponseHttp(
    val status: Int,
    val payload: Map<String, JsonElement>,
    val headers: Map<String, String> = emptyMap(),
)
