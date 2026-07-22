package id.walt.crypto2.signum

import kotlinx.coroutines.CancellationException

class SignumUserCancelledException(
    val reason: Throwable,
) : CancellationException(reason.message ?: "User cancelled cryptographic authorization")
