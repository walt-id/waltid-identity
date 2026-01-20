package id.walt.openid4vci.requests.token

import id.walt.openid4vci.Client
import id.walt.openid4vci.Session
import kotlin.time.Instant

/**
 * Token endpoint request contract (interface for extensibility).
 **/

interface AccessTokenRequest {
    val id: String
    val requestedAt: Instant
    var client: Client
    val grantTypes: Set<String>
    val handledGrantTypes: MutableSet<String>
    val requestedScopes: Set<String>
    val grantedScopes: MutableSet<String>
    val requestedAudience: Set<String>
    val grantedAudience: MutableSet<String>
    val requestForm: Map<String, List<String>>
    var session: Session?
    val issuerId: String?

    fun markGrantTypeHandled(grantType: String): AccessTokenRequest
    fun grantScopes(scopes: Collection<String>): AccessTokenRequest
    fun withGrantedScopes(scopes: Collection<String>): AccessTokenRequest
    fun grantAudience(audience: Collection<String>): AccessTokenRequest
    fun withGrantedAudience(audience: Collection<String>): AccessTokenRequest
    fun withClient(client: Client): AccessTokenRequest
    fun withSession(session: Session?): AccessTokenRequest
    fun withIssuer(id: String?): AccessTokenRequest
}
