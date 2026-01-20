package id.walt.openid4vci.requests.authorization

import id.walt.openid4vci.Client
import id.walt.openid4vci.ResponseModeType
import kotlin.time.Instant

/**
 * Authorization request contract (interface for extensibility).
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
    val responseMode: ResponseModeType
    val defaultResponseMode: ResponseModeType
    val requestForm: Map<String, List<String>>
    val issuerId: String?

    fun markResponseTypeHandled(responseType: String): AuthorizationRequest
    fun grantScopes(scopes: Collection<String>): AuthorizationRequest
    fun grantAudience(audience: Collection<String>): AuthorizationRequest
    fun withIssuer(id: String?): AuthorizationRequest
    fun withRedirectUri(uri: String?): AuthorizationRequest
    fun didHandleAllResponseTypes(): Boolean =
        responseTypes.isNotEmpty() && responseTypes.all { handledResponseTypes.contains(it) }
}
