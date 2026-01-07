package id.walt.openid4vci.core

import id.walt.openid4vci.ResponseModeType
import id.walt.openid4vci.Session
import id.walt.openid4vci.request.AccessTokenRequest
import id.walt.openid4vci.request.AuthorizationRequest

/**
 * Minimal OAuth2 provider contract scoped to the authorization-code/pre-authorized code grants.
 *
 * Methods follow the structure commonly used by established OAuth servers:
 * - `createAuthorizeRequest`/`createAuthorizeResponse` replace helpers by returning domain
 *   DTOs instead of writing to HTTP primitives.
 * - `writeAuthorizeError`/`writeAuthorizeResponse` encapsulate response-mode formatting for tests
 *   and framework integration.
 * - `createAccessRequest`/`createAccessResponse` cover the token endpoint, and the `write*` variants
 *   produce RFC6749-compliant bodies.
 *
 * Parameters will be changed. However, we have to keep the implementation framework-agnostic (Ktor, Spring).
 */
interface OAuth2Provider {
    fun createAuthorizeRequest(parameters: Map<String, String>): AuthorizeRequestResult
    suspend fun createAuthorizeResponse(request: AuthorizationRequest, session: Session): AuthorizeResponseResult
    fun writeAuthorizeError(request: AuthorizationRequest, error: OAuthError): AuthorizeHttpResponse
    fun writeAuthorizeResponse(request: AuthorizationRequest, response: AuthorizeResponse): AuthorizeHttpResponse
    fun createAccessRequest(parameters: Map<String, String>, session: Session? = null): AccessRequestResult
    suspend fun createAccessResponse(request: AccessTokenRequest): AccessResponseResult
    fun writeAccessError(request: AccessTokenRequest, error: OAuthError): AccessHttpResponse
    fun writeAccessResponse(request: AccessTokenRequest, response: AccessTokenResponse): AccessHttpResponse
}

data class OAuthError(
    val error: String,
    val description: String? = null,
)

sealed class AuthorizeRequestResult {
    data class Success(val request: AuthorizationRequest) : AuthorizeRequestResult()
    data class Failure(val error: OAuthError) : AuthorizeRequestResult()

    fun isSuccess(): Boolean = this is Success
}

data class AuthorizeResponse(
    val redirectUri: String,
    val parameters: Map<String, String>,
    val responseMode: ResponseModeType = ResponseModeType.QUERY,
    val headers: Map<String, String> = emptyMap(),
)

sealed class AuthorizeResponseResult {
    data class Success(val response: AuthorizeResponse) : AuthorizeResponseResult()
    data class Failure(val error: OAuthError) : AuthorizeResponseResult()

    fun isSuccess(): Boolean = this is Success
}

data class AuthorizeHttpResponse(
    val status: Int,
    val redirectUri: String?,
    val parameters: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
)


sealed class AccessRequestResult {
    data class Success(val request: AccessTokenRequest) : AccessRequestResult()
    data class Failure(val error: OAuthError) : AccessRequestResult()

    fun isSuccess(): Boolean = this is Success
}

data class AccessTokenResponse(
    val tokenType: String = TOKEN_TYPE_BEARER,
    val accessToken: String,
    val expiresIn: Long? = null,
    val extra: Map<String, Any?> = emptyMap(),
)

sealed class AccessResponseResult {
    data class Success(val response: AccessTokenResponse) : AccessResponseResult()
    data class Failure(val error: OAuthError) : AccessResponseResult()

    fun isSuccess(): Boolean = this is Success
}

data class AccessHttpResponse(
    val status: Int,
    val payload: Map<String, Any?>,
    val headers: Map<String, String> = emptyMap(),
)
