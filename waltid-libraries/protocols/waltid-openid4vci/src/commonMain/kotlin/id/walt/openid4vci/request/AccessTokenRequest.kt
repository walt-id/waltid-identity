package id.walt.openid4vci.request

import id.walt.openid4vci.Arguments
import id.walt.openid4vci.Session
import id.walt.openid4vci.append
import id.walt.openid4vci.has
import id.walt.openid4vci.newArguments

/**
 * Will be updated
 * Access token endpoint request passed to token handlers.
 */
class AccessTokenRequest(
    session: Session? = null,
) : BaseRequestContext(session = session),
    AccessRequester {

    private val grantTypes = newArguments()
    private val handledGrantTypes = newArguments()

    override fun getGrantTypes(): Arguments = grantTypes

    override fun setGrantTypes(grantTypes: Arguments) {
        this.grantTypes.clear()
        grantTypes.forEach { this.grantTypes.append(it) }
    }

    override fun appendGrantType(grantType: String) {
        grantTypes.append(grantType)
    }

    override fun markGrantTypeHandled(grantType: String) {
        handledGrantTypes.append(grantType)
    }

    override fun hasHandledGrantType(grantType: String): Boolean = handledGrantTypes.has(grantType)
}
