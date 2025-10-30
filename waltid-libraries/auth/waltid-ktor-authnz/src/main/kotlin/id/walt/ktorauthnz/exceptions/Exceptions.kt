package id.walt.ktorauthnz.exceptions

import id.walt.commons.web.AuthException
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

data class AuthenticationFailureException(override val message: String) : IllegalArgumentException(message)

@Suppress("NOTHING_TO_INLINE")
inline fun authFailure(message: String): Nothing = throw AuthenticationFailureException(message)

@OptIn(ExperimentalContracts::class)
fun authCheck(value: Boolean, exception: AuthException) {
    contract {
        returns() implies value
    }
    if (!value) throw exception
}
