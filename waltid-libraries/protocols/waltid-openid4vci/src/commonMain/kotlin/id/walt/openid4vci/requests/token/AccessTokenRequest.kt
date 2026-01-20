package id.walt.openid4vci.requests.token

import id.walt.openid4vci.Client
import id.walt.openid4vci.Session
import kotlin.time.Instant

/**
 * Access Token request for the Token endpoint (interface for extensibility).
 **/

interface AccessTokenRequest {
    val id: String
    val requestedAt: Instant
    val client: Client
    val grantTypes: Set<String>
    val handledGrantTypes: Set<String>
    val requestedScopes: Set<String>
    val grantedScopes: Set<String>
    val requestedAudience: Set<String>
    val grantedAudience: Set<String>
    val requestForm: Map<String, List<String>>
    val session: Session?
    val issClaim: String?

    fun markGrantTypeHandled(grantType: String): AccessTokenRequest
    fun grantScopes(scopes: Collection<String>): AccessTokenRequest
    fun withGrantedScopes(scopes: Collection<String>): AccessTokenRequest
    fun grantAudience(audience: Collection<String>): AccessTokenRequest
    fun withGrantedAudience(audience: Collection<String>): AccessTokenRequest
    fun withClient(client: Client): AccessTokenRequest
    fun withSession(session: Session?): AccessTokenRequest
    fun withIssuer(issClaim: String?): AccessTokenRequest
}
