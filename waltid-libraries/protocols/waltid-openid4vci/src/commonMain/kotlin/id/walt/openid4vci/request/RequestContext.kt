package id.walt.openid4vci.request

import id.walt.openid4vci.Arguments
import id.walt.openid4vci.Client
import id.walt.openid4vci.Parameters
import id.walt.openid4vci.ResponseModeType
import id.walt.openid4vci.Session
import kotlin.time.Instant

/**
 * Will be updated
 * Base contract for all request types.
 */
interface RequestContext {
    fun getID(): String
    fun setID(id: String)

    fun getRequestedAt(): Instant
    fun setRequestedAt(requestedAt: Instant)

    fun getClient(): Client
    fun setClient(client: Client)

    fun getRequestedScopes(): Arguments
    fun setRequestedScopes(scopes: Arguments)
    fun appendRequestedScope(scope: String)

    fun getGrantedScopes(): Arguments
    fun grantScope(scope: String)

    fun getRequestForm(): Parameters

    fun getSession(): Session?
    fun setSession(session: Session?)

    fun getRequestedAudience(): Arguments
    fun appendRequestedAudience(audience: String)

    fun getGrantedAudience(): Arguments
    fun grantAudience(audience: String)

    fun getIssuerId(): String?
    fun setIssuerId(id: String?)
}

interface AuthorizeRequester : RequestContext {
    fun getResponseTypes(): Arguments
    fun setResponseTypes(responseTypes: Arguments)
    fun setResponseTypeHandled(responseType: String)
    fun didHandleAllResponseTypes(): Boolean
    var redirectUri: String?
    var state: String?
    var responseMode: ResponseModeType
    var defaultResponseMode: ResponseModeType
}

interface AccessRequester : RequestContext {
    fun getGrantTypes(): Arguments
    fun setGrantTypes(grantTypes: Arguments)
    fun appendGrantType(grantType: String)
    fun markGrantTypeHandled(grantType: String)
    fun hasHandledGrantType(grantType: String): Boolean
}
