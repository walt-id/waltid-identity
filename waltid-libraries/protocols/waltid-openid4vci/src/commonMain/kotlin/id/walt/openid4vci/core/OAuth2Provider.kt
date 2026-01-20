package id.walt.openid4vci.core

import id.walt.openid4vci.Session
import id.walt.openid4vci.errors.OAuthError
import id.walt.openid4vci.requests.authorization.AuthorizationRequest
import id.walt.openid4vci.requests.authorization.AuthorizationRequestResult
import id.walt.openid4vci.requests.token.AccessTokenRequest
import id.walt.openid4vci.requests.token.AccessTokenRequestResult
import id.walt.openid4vci.responses.authorization.AuthorizationResponse
import id.walt.openid4vci.responses.authorization.AuthorizationResponseResult
import id.walt.openid4vci.responses.authorization.AuthorizationHttpResponse
import id.walt.openid4vci.responses.token.AccessTokenResponse
import id.walt.openid4vci.responses.token.AccessHttpResponse
import id.walt.openid4vci.responses.token.AccessResponseResult

/**
 * Minimal OAuth2 provider contract scoped to the authorization-code/pre-authorized code grants.
 *
 * Methods follow the structure commonly used by established OAuth servers:
 * - `createAuthorizationRequest`/`createAuthorizationResponse` replace helpers by returning domain
 *   DTOs instead of writing to HTTP primitives.
 * - `writeAuthorizationError`/`writeAuthorizationResponse` encapsulate response-mode formatting for tests
 *   and framework integration.
 * - `createAccessRequest`/`createAccessResponse` cover the token endpoint, and the `write*` variants
 *   produce RFC6749-compliant bodies.
 *
 * Parameters will be changed. However, we have to keep the implementation framework-agnostic (Ktor, Spring).
 */
interface OAuth2Provider {
    fun createAuthorizationRequest(parameters: Map<String, List<String>>): AuthorizationRequestResult
    suspend fun createAuthorizationResponse(authorizationRequest: AuthorizationRequest, session: Session): AuthorizationResponseResult
    fun writeAuthorizationError(authorizationRequest: AuthorizationRequest, error: OAuthError): AuthorizationHttpResponse
    fun writeAuthorizationResponse(authorizationRequest: AuthorizationRequest, response: AuthorizationResponse): AuthorizationHttpResponse
    fun createAccessRequest(parameters: Map<String, List<String>>, session: Session? = null): AccessTokenRequestResult
    suspend fun createAccessResponse(request: AccessTokenRequest): AccessResponseResult
    fun writeAccessError(request: AccessTokenRequest, error: OAuthError): AccessHttpResponse
    fun writeAccessResponse(request: AccessTokenRequest, response: AccessTokenResponse): AccessHttpResponse
}