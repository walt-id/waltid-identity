package id.walt.openid4vci.core

import id.walt.openid4vci.Session
import id.walt.openid4vci.errors.OAuthError
import id.walt.openid4vci.requests.token.AccessTokenRequest
import id.walt.openid4vci.requests.authorization.AuthorizationRequest
import id.walt.openid4vci.responses.token.AccessHttpResponse
import id.walt.openid4vci.responses.token.AccessResponseResult
import id.walt.openid4vci.responses.token.AccessTokenResponse
import id.walt.openid4vci.responses.authorization.AuthorizeHttpResponse
import id.walt.openid4vci.responses.authorization.AuthorizeResponse
import id.walt.openid4vci.responses.authorization.AuthorizeResponseResult

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
    fun createAuthorizeRequest(parameters: Map<String, List<String>>): AuthorizeRequestResult
    suspend fun createAuthorizeResponse(authorizationRequest: AuthorizationRequest, session: Session): AuthorizeResponseResult
    fun writeAuthorizeError(authorizationRequest: AuthorizationRequest, error: OAuthError): AuthorizeHttpResponse
    fun writeAuthorizeResponse(authorizationRequest: AuthorizationRequest, response: AuthorizeResponse): AuthorizeHttpResponse
    fun createAccessRequest(parameters: Map<String, List<String>>, session: Session? = null): AccessRequestResult
    suspend fun createAccessResponse(request: AccessTokenRequest): AccessResponseResult
    fun writeAccessError(request: AccessTokenRequest, error: OAuthError): AccessHttpResponse
    fun writeAccessResponse(request: AccessTokenRequest, response: AccessTokenResponse): AccessHttpResponse
}

sealed class AuthorizeRequestResult {
    data class Success(val request: AuthorizationRequest) : AuthorizeRequestResult()
    data class Failure(val error: OAuthError) : AuthorizeRequestResult()

    fun isSuccess(): Boolean = this is Success
}

sealed class AccessRequestResult {
    data class Success(val request: AccessTokenRequest) : AccessRequestResult()
    data class Failure(val error: OAuthError) : AccessRequestResult()

    fun isSuccess(): Boolean = this is Success
}
