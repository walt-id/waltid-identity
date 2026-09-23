package id.waltid.openid4vp.wallet.request

import id.walt.verifier.openid.models.authorization.AuthorizationRequest

sealed class ResolvedAuthorizationRequest {
    abstract val authorizationRequest: AuthorizationRequest

    data class Plain(
        override val authorizationRequest: AuthorizationRequest,
    ) : ResolvedAuthorizationRequest()

    data class UnsignedRequestObject(
        override val authorizationRequest: AuthorizationRequest,
        val requestObject: String,
    ) : ResolvedAuthorizationRequest()

    data class AuthenticatedRequestObject(
        override val authorizationRequest: AuthorizationRequest,
        val requestObject: String,
        /** Authentication facts established by [AuthorizationRequestResolver]. */
        val authentication: RequestObjectAuthentication,
    ) : ResolvedAuthorizationRequest()
}
