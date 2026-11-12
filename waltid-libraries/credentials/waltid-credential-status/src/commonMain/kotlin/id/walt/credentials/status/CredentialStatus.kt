@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.credentials.status

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** A status check is an issuer observation, independent of credential validity and acceptance. */
@Serializable
enum class StatusMeaning { CLEAR, REVOKED, INVALID, SUSPENDED, REFRESH, APPLICATION_SPECIFIC }

@Serializable
enum class StatusProtocol { TOKEN_STATUS_LIST, ISO_MSO_STATUS_LIST, BITSTRING_STATUS_LIST, STATUS_LIST_2021 }

@Serializable
enum class StatusFailure {
    REFERENCE_MALFORMED, REFERENCE_UNAUTHENTICATED, CREDENTIAL_EXPIRED,
    UNSUPPORTED_METHOD, UNSUPPORTED_PROOF, TRANSPORT, TIMEOUT, RATE_LIMITED,
    INVALID_SIGNATURE, UNAUTHORIZED_SIGNER, INVALID_VALIDITY, INVALID_LIST,
    RESOURCE_LIMIT, DISABLED, CONFLICTING_IRREVERSIBLE_STATUS,
}

/** Authenticated, applicable evidence. It contains no list URL, credential index or token. */
@Serializable
data class StatusObservation(
    val referenceId: String,
    val protocol: StatusProtocol,
    val purpose: String,
    val value: UInt,
    val checkedAt: Instant,
    val freshUntil: Instant,
    val validUntil: Instant? = null,
    @EncodeDefault val meaning: StatusMeaning = statusMeaning(protocol, purpose, value),
) {
    init {
        require(referenceId.isNotBlank() && purpose.isNotBlank())
        require(value <= 255u)
        require(meaning == statusMeaning(protocol, purpose, value))
        require(validUntil == null || freshUntil <= validUntil)
    }

    fun isFresh(at: Instant): Boolean = at >= checkedAt && at < freshUntil
}

/** Each reference retains its result and coverage, including a previous result after a failure. */
@Serializable
sealed class CredentialStatusEntry {
    abstract val referenceId: String

    @Serializable
    @SerialName("not_checked")
    data class NotChecked(
        override val referenceId: String,
        val protocol: StatusProtocol,
        val purpose: String,
    ) : CredentialStatusEntry()

    @Serializable
    @SerialName("unsupported")
    data class Unsupported(
        override val referenceId: String,
        val method: String,
    ) : CredentialStatusEntry()

    @Serializable
    @SerialName("observed")
    data class Observed(val observation: StatusObservation) : CredentialStatusEntry() {
        override val referenceId: String get() = observation.referenceId
    }

    @Serializable
    @SerialName("unavailable")
    data class Unavailable(
        override val referenceId: String,
        val reason: StatusFailure,
        val attemptedAt: Instant,
        val previous: StatusObservation? = null,
    ) : CredentialStatusEntry() {
        init { require(previous == null || previous.referenceId == referenceId) }
    }

    fun lastObservation(): StatusObservation? = when (this) {
        is Observed -> observation
        is Unavailable -> previous
        else -> null
    }
}

/** Local read metadata. An empty list means no status reference; it never means all-clear. */
@Serializable
data class CredentialStatusMetadata(val entries: List<CredentialStatusEntry> = emptyList()) {
    init { require(entries.map(CredentialStatusEntry::referenceId).distinct().size == entries.size) }

    val provided: Boolean get() = entries.isNotEmpty()
}

/** Failures carry a stable category; exception text is never a user-facing status API. */
class CredentialStatusException(val reason: StatusFailure, cause: Throwable? = null) :
    Exception(reason.name, cause)

private fun statusMeaning(protocol: StatusProtocol, purpose: String, value: UInt): StatusMeaning = when {
    protocol == StatusProtocol.TOKEN_STATUS_LIST -> when (value) {
        0u -> StatusMeaning.CLEAR
        1u -> StatusMeaning.INVALID
        2u -> StatusMeaning.SUSPENDED
        else -> StatusMeaning.APPLICATION_SPECIFIC
    }
    purpose !in setOf("revocation", "suspension", "refresh") -> StatusMeaning.APPLICATION_SPECIFIC
    value == 0u -> StatusMeaning.CLEAR
    purpose == "revocation" -> StatusMeaning.REVOKED
    purpose == "suspension" -> StatusMeaning.SUSPENDED
    purpose == "refresh" -> StatusMeaning.REFRESH
    else -> StatusMeaning.APPLICATION_SPECIFIC
}
