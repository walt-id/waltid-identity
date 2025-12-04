package id.walt.openid4vci.request

import id.walt.openid4vci.Arguments
import id.walt.openid4vci.Client
import id.walt.openid4vci.DefaultClient
import id.walt.openid4vci.Parameters
import id.walt.openid4vci.Session
import id.walt.openid4vci.append
import id.walt.openid4vci.newArguments
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random
import kotlin.time.Instant
import kotlin.time.ExperimentalTime

/**
 * Will be updated
 * Base implementation for [RequestContext].
 */
open class BaseRequestContext @OptIn(ExperimentalTime::class) constructor(
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
        if (requestId.isNullOrEmpty()) {
            requestId = generateId()
        }
        return requestId!!
    }

    override fun setID(id: String) {
        this.requestId = id
    }

    @OptIn(ExperimentalTime::class)
    override fun getRequestedAt(): Instant = requestedAt

    @OptIn(ExperimentalTime::class)
    override fun setRequestedAt(requestedAt: Instant) {
        this.requestedAt = requestedAt
    }

    override fun getClient(): Client = client

    override fun setClient(client: Client) {
        this.client = client
    }

    override fun getRequestedScopes(): Arguments = requestedScope

    override fun setRequestedScopes(scopes: Arguments) {
        requestedScope.clear()
        scopes.forEach { appendRequestedScope(it) }
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

    override fun setSession(session: Session) {
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

    @OptIn(ExperimentalEncodingApi::class)
    private fun generateId(): String =
        Base64.UrlSafe.encode(Random.nextBytes(16))
}
