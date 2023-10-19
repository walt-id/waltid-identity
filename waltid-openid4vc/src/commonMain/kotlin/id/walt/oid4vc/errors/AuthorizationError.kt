package id.walt.oid4vc.errors

import id.walt.oid4vc.requests.IAuthorizationRequest
import id.walt.oid4vc.responses.AuthorizationCodeResponse
import id.walt.oid4vc.responses.AuthorizationErrorCode
import id.walt.oid4vc.responses.PushedAuthorizationResponse

class AuthorizationError(
    val authorizationRequest: IAuthorizationRequest,
    val errorCode: AuthorizationErrorCode,
    override val message: String? = null
) : Exception() {
    fun toAuthorizationErrorResponse() = AuthorizationCodeResponse.error(errorCode, message)
    fun toPushedAuthorizationErrorResponse() = PushedAuthorizationResponse.error(errorCode, message)
}
