package id.walt.openid4vci.validation

import id.walt.openid4vci.Session
import id.walt.openid4vci.core.AccessRequestResult
import id.walt.openid4vci.core.AuthorizeRequestResult

fun interface AuthorizeRequestValidator {
    fun validate(parameters: Map<String, String>): AuthorizeRequestResult
}

fun interface AccessRequestValidator {
    fun validate(parameters: Map<String, String>, session: Session): AccessRequestResult
}
