package id.walt.credentials.status

import kotlin.time.Instant

val CredentialStatusEntry.protocol: StatusProtocol? get() = when (this) {
    is CredentialStatusEntry.Token -> StatusProtocol.TOKEN_STATUS_LIST
    is CredentialStatusEntry.Mso -> StatusProtocol.ISO_MSO_STATUS_LIST
    is CredentialStatusEntry.Bitstring -> StatusProtocol.BITSTRING_STATUS_LIST
    is CredentialStatusEntry.Legacy -> StatusProtocol.STATUS_LIST_2021
    is CredentialStatusEntry.Unsupported, is CredentialStatusEntry.Malformed -> null
}

val CredentialStatusEntry.purposeName: String? get() = when (this) {
    is CredentialStatusEntry.Token -> "token"
    is CredentialStatusEntry.Mso -> "revocation"
    is CredentialStatusEntry.Bitstring -> purpose.value
    is CredentialStatusEntry.Legacy -> purpose.value
    is CredentialStatusEntry.Unsupported, is CredentialStatusEntry.Malformed -> null
}

private val CredentialStatusEntry.checkState: Check<*>? get() = when (this) {
    is CredentialStatusEntry.Token -> check
    is CredentialStatusEntry.Mso -> check
    is CredentialStatusEntry.Bitstring -> check
    is CredentialStatusEntry.Legacy -> check
    is CredentialStatusEntry.Unsupported, is CredentialStatusEntry.Malformed -> null
}

fun CredentialStatusEntry.lastObservation(): Observation<*>? = checkState?.lastObservation()
val CredentialStatusEntry.lastAttemptAt: Instant? get() = when (val check = checkState) {
    is Check.Failed -> check.attemptedAt
    is Check.Conflict -> check.attemptedAt
    else -> null
}
val CredentialStatusEntry.failure: CheckFailure? get() = (checkState as? Check.Failed)?.cause
val CredentialStatusEntry.hasConflict: Boolean get() = checkState is Check.Conflict
val CredentialStatusEntry.hasCheckError: Boolean get() = this is CredentialStatusEntry.Malformed || failure != null || hasConflict
val CredentialStatus.hasCheckError: Boolean get() = this is CredentialStatus.DiscoveryFailed || entries.any { it.hasCheckError }

/** Meaning is derived from the typed owner and its observed scalar, never supplied independently. */
val CredentialStatusEntry.meaning: StatusMeaning? get() = when (this) {
    is CredentialStatusEntry.Token -> check.lastObservation()?.value?.let(::tokenMeaning)
    is CredentialStatusEntry.Mso -> check.lastObservation()?.value?.let { if (it == MsoStatus.REVOKED) StatusMeaning.REVOKED else StatusMeaning.CLEAR }
    is CredentialStatusEntry.Bitstring -> check.lastObservation()?.value?.let { bitstringMeaning(purpose, it) }
    is CredentialStatusEntry.Legacy -> check.lastObservation()?.value?.let { legacyMeaning(purpose, it) }
    is CredentialStatusEntry.Unsupported, is CredentialStatusEntry.Malformed -> null
}

internal val CredentialStatusEntry.irreversible: Boolean get() =
    protocol != StatusProtocol.TOKEN_STATUS_LIST && meaning in setOf(StatusMeaning.REVOKED, StatusMeaning.REFRESH)

private fun tokenMeaning(value: TokenStatus): StatusMeaning = when (value) {
    TokenStatus.Known.CLEAR -> StatusMeaning.CLEAR
    TokenStatus.Known.INVALID -> StatusMeaning.INVALID
    TokenStatus.Known.SUSPENDED -> StatusMeaning.SUSPENDED
    is TokenStatus.ApplicationSpecific -> StatusMeaning.APPLICATION_SPECIFIC
    is TokenStatus.Reserved -> StatusMeaning.RESERVED
}
private fun bitstringMeaning(purpose: BitstringPurpose, value: UByte): StatusMeaning = when (purpose) {
    BitstringPurpose.Known.MESSAGE, is BitstringPurpose.Extension -> StatusMeaning.APPLICATION_SPECIFIC
    BitstringPurpose.Known.REVOCATION -> if (value == 0u.toUByte()) StatusMeaning.CLEAR else StatusMeaning.REVOKED
    BitstringPurpose.Known.SUSPENSION -> if (value == 0u.toUByte()) StatusMeaning.CLEAR else StatusMeaning.SUSPENDED
    BitstringPurpose.Known.REFRESH -> if (value == 0u.toUByte()) StatusMeaning.CLEAR else StatusMeaning.REFRESH
}
private fun legacyMeaning(purpose: LegacyPurpose, value: Boolean): StatusMeaning = when (purpose) {
    is LegacyPurpose.Extension -> StatusMeaning.APPLICATION_SPECIFIC
    LegacyPurpose.Known.REVOCATION -> if (value) StatusMeaning.REVOKED else StatusMeaning.CLEAR
    LegacyPurpose.Known.SUSPENSION -> if (value) StatusMeaning.SUSPENDED else StatusMeaning.CLEAR
}

/** One eligibility rule for presentation and wallet admission; Force never bypasses validity. */
fun CredentialStatusEntry.isCheckable(at: Instant): Boolean {
    if (checkState == null) return false
    return when (val cause = failure) {
        CheckFailure.Reason.CREDENTIAL_EXPIRED, CheckFailure.Reason.UNSUPPORTED_PROOF -> false
        is CheckFailure.NotYetValid -> at >= cause.validFrom
        is CheckFailure.RateLimited -> cause.retryAt == null || at >= cause.retryAt
        else -> true
    }
}

/** Earliest eligibility boundary, separate from the host's retry/backoff policy. */
val CredentialStatusEntry.eligibleAt: Instant? get() = when (val cause = failure) {
    is CheckFailure.NotYetValid -> cause.validFrom
    is CheckFailure.RateLimited -> cause.retryAt
    else -> null
}

fun CredentialStatusEntry.matches(other: CredentialStatusEntry): Boolean =
    referenceId == other.referenceId && when (this) {
        is CredentialStatusEntry.Token -> other is CredentialStatusEntry.Token
        is CredentialStatusEntry.Mso -> other is CredentialStatusEntry.Mso
        is CredentialStatusEntry.Bitstring -> other is CredentialStatusEntry.Bitstring && purpose == other.purpose
        is CredentialStatusEntry.Legacy -> other is CredentialStatusEntry.Legacy && purpose == other.purpose
        is CredentialStatusEntry.Unsupported -> other is CredentialStatusEntry.Unsupported && method == other.method
        is CredentialStatusEntry.Malformed -> other is CredentialStatusEntry.Malformed && problem == other.problem
    }

/** Retain earlier evidence only for the exact same owner. No caller can rebind its protocol/purpose. */
fun CredentialStatusEntry.mergePrevious(previous: CredentialStatusEntry, at: Instant): CredentialStatusEntry {
    require(matches(previous)) { "Credential status reference does not match" }
    return when {
        this is CredentialStatusEntry.Token && previous is CredentialStatusEntry.Token ->
            CredentialStatusEntry.Token(referenceId, check.merge(previous.check, at, previous.irreversible, ::tokenMeaning))
        this is CredentialStatusEntry.Mso && previous is CredentialStatusEntry.Mso ->
            CredentialStatusEntry.Mso(referenceId, check.merge(previous.check, at, previous.irreversible, { if (it == MsoStatus.REVOKED) StatusMeaning.REVOKED else StatusMeaning.CLEAR }))
        this is CredentialStatusEntry.Bitstring && previous is CredentialStatusEntry.Bitstring ->
            CredentialStatusEntry.Bitstring(referenceId, purpose, check.merge(previous.check, at, previous.irreversible,
                { bitstringMeaning(purpose, it) }))
        this is CredentialStatusEntry.Legacy && previous is CredentialStatusEntry.Legacy ->
            CredentialStatusEntry.Legacy(referenceId, purpose, check.merge(previous.check, at, previous.irreversible, { legacyMeaning(purpose, it) }))
        else -> this
    }
}

private fun <V : Any> Check<V>.merge(previous: Check<V>, at: Instant, irreversible: Boolean, meaning: (V) -> StatusMeaning): Check<V> {
    val old = previous.lastObservation() ?: return this
    return when (this) {
        is Check.Failed -> Check.Failed(attemptedAt, cause, old)
        is Check.Observed -> if (irreversible && meaning(old.value) != meaning(observation.value)) Check.Conflict(at, old) else this
        else -> this
    }
}

/** Restored evidence keeps its check time and cannot gain a longer fresh interval. */
fun CredentialStatusEntry.historical(at: Instant): CredentialStatusEntry = when (this) {
    is CredentialStatusEntry.Token -> CredentialStatusEntry.Token(referenceId, check.historical(at))
    is CredentialStatusEntry.Mso -> CredentialStatusEntry.Mso(referenceId, check.historical(at))
    is CredentialStatusEntry.Bitstring -> CredentialStatusEntry.Bitstring(referenceId, purpose, check.historical(at))
    is CredentialStatusEntry.Legacy -> CredentialStatusEntry.Legacy(referenceId, purpose, check.historical(at))
    is CredentialStatusEntry.Unsupported, is CredentialStatusEntry.Malformed -> this
}
private fun <V : Any> Check<V>.historical(at: Instant): Check<V> = when (this) {
    Check.NotChecked -> this
    is Check.Observed -> Check.Observed(observation.historical(at))
    is Check.Failed -> if (cause == CheckFailure.Reason.UNSUPPORTED_PROOF) {
        previous?.let { Check.Observed(it.historical(at)) } ?: Check.NotChecked
    } else Check.Failed(attemptedAt, cause, previous?.historical(at))
    is Check.Conflict -> Check.Conflict(attemptedAt, retained.historical(at))
}

fun CredentialStatusEntry.withFailure(cause: CheckFailure, at: Instant): CredentialStatusEntry = when (this) {
    is CredentialStatusEntry.Token -> CredentialStatusEntry.Token(referenceId, Check.Failed(at, cause, check.lastObservation()))
    is CredentialStatusEntry.Mso -> CredentialStatusEntry.Mso(referenceId, Check.Failed(at, cause, check.lastObservation()))
    is CredentialStatusEntry.Bitstring -> CredentialStatusEntry.Bitstring(referenceId, purpose, Check.Failed(at, cause, check.lastObservation()))
    is CredentialStatusEntry.Legacy -> CredentialStatusEntry.Legacy(referenceId, purpose, Check.Failed(at, cause, check.lastObservation()))
    is CredentialStatusEntry.Unsupported, is CredentialStatusEntry.Malformed -> this
}
