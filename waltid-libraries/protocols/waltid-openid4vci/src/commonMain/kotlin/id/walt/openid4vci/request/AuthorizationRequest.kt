package id.walt.openid4vci.request

import id.walt.openid4vci.Arguments
import id.walt.openid4vci.ResponseModeType
import id.walt.openid4vci.append
import id.walt.openid4vci.has
import id.walt.openid4vci.newArguments

/**
 * Will be updated
 * Authorization endpoint request object shared by authorize handlers.
 */
class AuthorizationRequest : BaseRequestContext(),
    AuthorizeRequester {

    private val responseTypesInternal = newArguments()
    private val handledResponseTypesInternal = newArguments()

    override fun getResponseTypes(): Arguments = responseTypesInternal

    override fun setResponseTypes(responseTypes: Arguments) {
        responseTypesInternal.clear()
        responseTypes.forEach { responseTypesInternal.append(it) }
    }

    override fun setResponseTypeHandled(responseType: String) {
        handledResponseTypesInternal.append(responseType)
    }

    override fun didHandleAllResponseTypes(): Boolean =
        responseTypesInternal.isNotEmpty() &&
                responseTypesInternal.all { handledResponseTypesInternal.has(it) }

    override var redirectUri: String? = null
    override var state: String? = null
    override var responseMode: ResponseModeType = ResponseModeType.DEFAULT
    override var defaultResponseMode: ResponseModeType = ResponseModeType.DEFAULT
}
