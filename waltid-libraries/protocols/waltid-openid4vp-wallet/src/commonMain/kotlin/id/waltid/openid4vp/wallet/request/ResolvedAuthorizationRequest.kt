package id.waltid.openid4vp.wallet.request

import id.walt.verifier.openid.models.authorization.AuthorizationRequest

sealed class ResolvedAuthorizationRequest {
    abstract val authorizationRequest: AuthorizationRequest

    data class Plain(
        override val authorizationRequest: AuthorizationRequest,
    ) : ResolvedAuthorizationRequest()

    data class WithRequestObject(
        override val authorizationRequest: AuthorizationRequest,
        val requestObject: String,
    ) : ResolvedAuthorizationRequest()
}
