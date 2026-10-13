package id.walt.ktorauthnz.methods.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * A one-time code to send for the `email-code` method. [email] is the address the user entered when the code is the
 * first step (passwordless login); for a later step it is null and [accountId] names the account whose address the
 * application sends to. [tenant] is set under an `authnzTenant` scope.
 */
data class EmailCodeDelivery(
    val email: String?,
    val accountId: String?,
    val code: String,
    val tenant: String?,
)

/** Settings of the `email-code` method; [send] delivers a code, e.g. by your mail service. */
data class EmailCodeSettings(
    val codeLength: Int = 6,
    val codeLifetime: Duration = 10.minutes,
    /** Codes sent per session, and per address an hour; more are refused with 429. */
    val maxSends: Int = 5,
    val send: suspend (EmailCodeDelivery) -> Unit,
) {
    init {
        require(codeLength in 6..10) { "codeLength must be between 6 and 10" }
    }
}
