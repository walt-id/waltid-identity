package id.walt.openid4vci.validation

import id.walt.openid4vci.Session
import id.walt.openid4vci.requests.authorization.AuthorizationRequestResult
import id.walt.openid4vci.requests.token.AccessTokenRequestResult

fun interface AuthorizationRequestValidator {
    fun validate(parameters: Map<String, List<String>>): AuthorizationRequestResult
}

fun interface AccessTokenRequestValidator {
    fun validate(parameters: Map<String, List<String>>, session: Session): AccessTokenRequestResult
}
