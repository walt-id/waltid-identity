package id.walt.openid4vci.requests.token

import id.walt.openid4vci.Client
import id.walt.openid4vci.Session
import id.walt.openid4vci.requests.generateRequestId
import kotlin.time.Clock
import kotlin.time.Instant

data class DefaultAccessTokenRequest(
    override val id: String = generateRequestId(),
    override val requestedAt: Instant = Clock.System.now(),
    override val client: Client,
    override val grantTypes: Set<String>,
    override val handledGrantTypes: Set<String> = emptySet(),
    override val requestedScopes: Set<String> = emptySet(),
    override val grantedScopes: Set<String> = emptySet(),
    override val requestedAudience: Set<String> = emptySet(),
    override val grantedAudience: Set<String> = emptySet(),
    override val requestForm: Map<String, List<String>> = emptyMap(),
    override val session: Session? = null,
    override val issuerId: String? = null,
) : AccessTokenRequest {
    override fun markGrantTypeHandled(grantType: String): AccessTokenRequest =
        copy(handledGrantTypes = handledGrantTypes + grantType)

    override fun grantScopes(scopes: Collection<String>): AccessTokenRequest =
        copy(grantedScopes = grantedScopes + scopes)

    override fun withGrantedScopes(scopes: Collection<String>): AccessTokenRequest =
        copy(grantedScopes = scopes.toSet())

    override fun grantAudience(audience: Collection<String>): AccessTokenRequest =
        copy(grantedAudience = grantedAudience + audience)

    override fun withGrantedAudience(audience: Collection<String>): AccessTokenRequest =
        copy(grantedAudience = audience.toSet())

    override fun withClient(client: Client): AccessTokenRequest =
        copy(client = client)

    override fun withSession(session: Session?): AccessTokenRequest =
        copy(session = session)

    override fun withIssuer(id: String?): AccessTokenRequest =
        copy(issuerId = id)

    fun hasHandledGrantType(grantType: String): Boolean = handledGrantTypes.contains(grantType)
}
