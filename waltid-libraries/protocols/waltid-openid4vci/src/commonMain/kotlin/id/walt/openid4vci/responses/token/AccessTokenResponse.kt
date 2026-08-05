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
    val refreshToken: String? = null,
    val scope: String? = null,
    val extra: Map<String, Any?> = emptyMap(),
) {
    init {
        require(extra.keys.none { it in STANDARD_RESPONSE_PARAMETERS }) {
            "extra must not override standard token response fields"
        }
    }

    private companion object {
        val STANDARD_RESPONSE_PARAMETERS = setOf(
            "access_token",
            "token_type",
            "expires_in",
            "refresh_token",
            "scope",
        )
    }
}

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
    data class Failure(val error: OAuthError) : AccessTokenResponseResult() {
        // Outside the primary constructor: this type is published, so a new parameter would change
        // the generated constructor and `copy` signatures for compiled consumers. Consequently
        // `copy()` does not carry it over, and it takes no part in equals/hashCode.
        var context: TokenFailureContext? = null
            internal set

        internal fun withContext(context: TokenFailureContext): Failure = apply { this.context = context }
    }

    fun isSuccess(): Boolean = this is Success
}

internal fun tokenFailure(
    error: OAuthError,
    sessionSubject: String?,
    stage: TokenFailureStage = TokenFailureStage.UNSPECIFIED,
): AccessTokenResponseResult.Failure {
    val failure = AccessTokenResponseResult.Failure(error)
    return sessionSubject
        ?.takeIf { it.isNotBlank() }
        ?.let { failure.withContext(TokenFailureContext(sessionSubject = it, stage = stage)) }
        ?: failure
}

data class TokenFailureContext(
    /** The grant's session subject, verbatim. Issuers that key sessions by subject can correlate on it. */
    val sessionSubject: String,
    val stage: TokenFailureStage = TokenFailureStage.UNSPECIFIED,
)

enum class TokenFailureStage {
    GRANT_VALIDATION,
    CLIENT_AUTHENTICATION,
    TX_CODE_VALIDATION,
    REDIRECT_URI_VALIDATION,
    SCOPE_VALIDATION,
    UNSPECIFIED,
}

data class AccessTokenResponseHttp(
    val status: Int,
    val payload: Map<String, JsonElement>,
    val headers: Map<String, String> = emptyMap(),
)
