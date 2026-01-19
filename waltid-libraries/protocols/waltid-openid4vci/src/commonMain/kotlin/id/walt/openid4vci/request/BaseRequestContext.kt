@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)

package id.walt.openid4vci.request

import id.walt.openid4vci.Arguments
import id.walt.openid4vci.Client
import id.walt.openid4vci.DefaultClient
import id.walt.openid4vci.Parameters
import id.walt.openid4vci.Session
import id.walt.openid4vci.append
import id.walt.openid4vci.newArguments
import kotlin.io.encoding.Base64
import kotlin.time.Instant
import korlibs.crypto.SecureRandom

/**
 * Will be updated
 * Base implementation for [RequestContext].
 */
open class BaseRequestContext(
    private var requestId: String? = null,
    private var requestedAt: Instant = kotlin.time.Clock.System.now(),
    private var client: Client = DefaultClient(),
    private val requestedScope: Arguments = newArguments(),
    private val grantedScope: Arguments = newArguments(),
    private val requestedAudience: Arguments = newArguments(),
    private val grantedAudience: Arguments = newArguments(),
    private val form: Parameters = Parameters(),
    private var session: Session? = null,
    private var issuerId: String? = null,
) : RequestContext {

    override fun getID(): String {
        val current = requestId?.takeIf { it.isNotBlank() }
        return current ?: generateId().also { requestId = it }
    }

    override fun setID(id: String) {
        require(id.isNotBlank()) { "id must not be blank" }
        this.requestId = id
    }

    override fun getRequestedAt(): Instant = requestedAt

    override fun setRequestedAt(requestedAt: Instant) {
        this.requestedAt = requestedAt
    }

    override fun getClient(): Client = client

    override fun setClient(client: Client) {
        this.client = client
    }

    override fun getRequestedScopes(): Arguments = requestedScope

    override fun setRequestedScopes(scopes: Arguments) {
        if (scopes === requestedScope) return
        val copy = scopes.toList()
        requestedScope.clear()
        copy.forEach { appendRequestedScope(it) }
    }

    override fun appendRequestedScope(scope: String) {
        requestedScope.append(scope)
    }

    override fun getGrantedScopes(): Arguments = grantedScope

    override fun grantScope(scope: String) {
        grantedScope.append(scope)
    }

    override fun getRequestForm(): Parameters = form

    override fun getSession(): Session? = session

    override fun setSession(session: Session?) {
        this.session = session
    }

    override fun getRequestedAudience(): Arguments = requestedAudience

    override fun appendRequestedAudience(audience: String) {
        requestedAudience.append(audience)
    }

    override fun getGrantedAudience(): Arguments = grantedAudience

    override fun grantAudience(audience: String) {
        grantedAudience.append(audience)
    }

    override fun getIssuerId(): String? = issuerId

    override fun setIssuerId(id: String?) {
        issuerId = id
    }

    private fun generateId(): String =
        Base64.UrlSafe.encode(ByteArray(16).also { SecureRandom.nextBytes(it) })
}
