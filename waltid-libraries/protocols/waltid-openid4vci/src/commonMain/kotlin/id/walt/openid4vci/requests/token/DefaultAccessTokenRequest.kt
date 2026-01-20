package id.walt.openid4vci.requests.token

import id.walt.openid4vci.Client
import id.walt.openid4vci.Session
import id.walt.openid4vci.requests.generateRequestId
import kotlin.time.Clock
import kotlin.time.Instant

data class DefaultAccessTokenRequest(
    override val id: String = generateRequestId(),
    override val requestedAt: Instant = Clock.System.now(),
    override var client: Client,
    override val grantTypes: Set<String>,
    override val handledGrantTypes: MutableSet<String> = mutableSetOf(),
    override val requestedScopes: Set<String> = emptySet(),
    override val grantedScopes: MutableSet<String> = mutableSetOf(),
    override val requestedAudience: Set<String> = emptySet(),
    override val grantedAudience: MutableSet<String> = mutableSetOf(),
    override val requestForm: Map<String, List<String>> = emptyMap(),
    override var session: Session? = null,
    override val issuerId: String? = null,
) : AccessTokenRequest {
    override fun markGrantTypeHandled(grantType: String): AccessTokenRequest {
        handledGrantTypes.add(grantType)
        return this
    }

    override fun grantScopes(scopes: Collection<String>): AccessTokenRequest {
        grantedScopes.addAll(scopes)
        return this
    }

    override fun withGrantedScopes(scopes: Collection<String>): AccessTokenRequest {
        grantedScopes.clear()
        grantedScopes.addAll(scopes)
        return this
    }

    override fun grantAudience(audience: Collection<String>): AccessTokenRequest {
        grantedAudience.addAll(audience)
        return this
    }

    override fun withGrantedAudience(audience: Collection<String>): AccessTokenRequest {
        grantedAudience.clear()
        grantedAudience.addAll(audience)
        return this
    }

    override fun withClient(client: Client): AccessTokenRequest {
        this.client = client
        return this
    }

    override fun withSession(session: Session?): AccessTokenRequest {
        this.session = session
        return this
    }

    override fun withIssuer(id: String?): AccessTokenRequest =
        copy(issuerId = id)

    fun hasHandledGrantType(grantType: String): Boolean = handledGrantTypes.contains(grantType)
}
