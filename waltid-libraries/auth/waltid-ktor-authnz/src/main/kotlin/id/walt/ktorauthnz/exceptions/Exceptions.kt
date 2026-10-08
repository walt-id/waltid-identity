package id.walt.ktorauthnz.exceptions

import io.ktor.http.HttpStatusCode
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/** A failed authentication without a more specific [AuthException]; answered 401 like the others. */
class AuthenticationFailureException(override val message: String) :
    AuthException(message, HttpStatusCode.Unauthorized)

/** Fails the authentication with [message] (401). */
@Suppress("NOTHING_TO_INLINE")
inline fun authFailure(message: String): Nothing = throw AuthenticationFailureException(message)

/** Fails the authentication with [exception] unless [value] holds. */
@OptIn(ExperimentalContracts::class)
fun authCheck(value: Boolean, exception: AuthException) {
    contract {
        returns() implies value
    }
    if (!value) throw exception
}
