package id.walt.oid4vc.errors

import id.walt.oid4vc.requests.TokenRequest
import id.walt.oid4vc.responses.TokenErrorCode
import id.walt.oid4vc.responses.TokenResponse

class TokenError(val tokenRequest: TokenRequest, val errorCode: TokenErrorCode, override val message: String? = null) :
    Exception() {
    fun toAuthorizationErrorResponse() = TokenResponse.error(errorCode, message)
}
