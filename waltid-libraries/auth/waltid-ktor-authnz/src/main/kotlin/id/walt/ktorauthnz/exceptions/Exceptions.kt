package id.walt.ktorauthnz.exceptions

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

data class AuthenticationFailureException(override val message: String) : IllegalArgumentException(message)

@Suppress("NOTHING_TO_INLINE")
inline fun authFailure(message: String): Nothing = throw AuthenticationFailureException(message)

@OptIn(ExperimentalContracts::class)
inline fun authCheck(value: Boolean, lazyMessage: () -> Any): Unit {
    contract {
        returns() implies value
    }
    if (!value) authFailure(lazyMessage().toString())
}
