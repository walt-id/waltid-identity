package id.walt.openid4vci.requests.authorization

import id.walt.openid4vci.Client
import id.walt.openid4vci.ResponseMode
import kotlin.time.Instant

/**
 * Authorization Request for the Authorization Endpoint (interface for extensibility).
**/
interface AuthorizationRequest {
    val id: String
    val requestedAt: Instant
    val client: Client
    val responseTypes: Set<String>
    val handledResponseTypes: Set<String>
    val requestedScopes: Set<String>
    val grantedScopes: Set<String>
    val requestedAudience: Set<String>
    val grantedAudience: Set<String>
    val redirectUri: String?
    val state: String?
    val issuerState: String?
    val responseMode: ResponseMode
    val defaultResponseMode: ResponseMode
    val requestForm: Map<String, List<String>>
    val issClaim: String?

    fun markResponseTypeHandled(responseType: String): AuthorizationRequest
    fun grantScopes(scopes: Collection<String>): AuthorizationRequest
    fun grantAudience(audience: Collection<String>): AuthorizationRequest
    fun withIssuer(issClaim: String?): AuthorizationRequest
    fun withRedirectUri(uri: String?): AuthorizationRequest
    fun didHandleAllResponseTypes(): Boolean =
        responseTypes.isNotEmpty() && responseTypes.all { handledResponseTypes.contains(it) }
}
