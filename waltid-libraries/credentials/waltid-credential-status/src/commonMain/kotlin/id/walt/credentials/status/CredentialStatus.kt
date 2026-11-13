package id.walt.credentials.status

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlin.time.Instant

/** Issuer meaning, separate from freshness, check failures and verifier acceptance. */
@Serializable
enum class StatusMeaning { CLEAR, REVOKED, INVALID, SUSPENDED, REFRESH, APPLICATION_SPECIFIC, RESERVED }

@Serializable
enum class StatusProtocol { TOKEN_STATUS_LIST, ISO_MSO_STATUS_LIST, BITSTRING_STATUS_LIST, STATUS_LIST_2021 }

@Serializable(with = TokenStatusSerializer::class)
sealed interface TokenStatus {
    val code: UByte

    enum class Known(override val code: UByte) : TokenStatus { CLEAR(0u), INVALID(1u), SUSPENDED(2u) }
    class ApplicationSpecific private constructor(override val code: UByte) : TokenStatus {
        init { require(code.toInt() == 3 || code.toInt() in 12..15) }
        override fun equals(other: Any?) = other is ApplicationSpecific && code == other.code
        override fun hashCode() = code.hashCode()
        companion object { internal fun create(code: UByte) = ApplicationSpecific(code) }
    }
    class Reserved private constructor(override val code: UByte) : TokenStatus {
        init { require(code.toInt() in 4..11 || code.toInt() in 16..255) }
        override fun equals(other: Any?) = other is Reserved && code == other.code
        override fun hashCode() = code.hashCode()
        companion object { internal fun create(code: UByte) = Reserved(code) }
    }

    companion object {
        fun fromCode(code: UByte): TokenStatus = when (code.toInt()) {
            0 -> Known.CLEAR
            1 -> Known.INVALID
            2 -> Known.SUSPENDED
            3, in 12..15 -> ApplicationSpecific.create(code)
            else -> Reserved.create(code)
        }
    }
}

@Serializable
enum class MsoStatus { CLEAR, REVOKED }

@Serializable(with = BitstringPurposeSerializer::class)
sealed interface BitstringPurpose {
    val value: String
    enum class Known(override val value: String) : BitstringPurpose {
        REVOCATION("revocation"), SUSPENSION("suspension"), REFRESH("refresh"), MESSAGE("message")
    }
    class Extension private constructor(override val value: String) : BitstringPurpose {
        init { require(value.isNotBlank() && Known.entries.none { it.value == value }) }
        override fun equals(other: Any?) = other is Extension && value == other.value
        override fun hashCode() = value.hashCode()
        companion object { internal fun create(value: String) = Extension(value) }
    }
    companion object {
        fun fromValue(value: String): BitstringPurpose = Known.entries.firstOrNull { it.value == value } ?: Extension.create(value)
    }
}

@Serializable(with = LegacyPurposeSerializer::class)
sealed interface LegacyPurpose {
    val value: String
    enum class Known(override val value: String) : LegacyPurpose { REVOCATION("revocation"), SUSPENSION("suspension") }
    class Extension private constructor(override val value: String) : LegacyPurpose {
        init { require(value.isNotBlank() && Known.entries.none { it.value == value }) }
        override fun equals(other: Any?) = other is Extension && value == other.value
        override fun hashCode() = value.hashCode()
        companion object { internal fun create(value: String) = Extension(value) }
    }
    companion object {
        fun fromValue(value: String): LegacyPurpose = Known.entries.firstOrNull { it.value == value } ?: Extension.create(value)
    }
}

/** Dated value within its owning entry. No duplicated identity, protocol, purpose or meaning. */
@Serializable
class Observation<out V : Any> internal constructor(
    val value: V,
    val checkedAt: Instant,
    val freshUntil: Instant,
    val validUntil: Instant? = null,
) {
    init { require(validUntil == null || freshUntil <= validUntil) }
    fun isFresh(at: Instant): Boolean = checkedAt <= at && at < freshUntil
    internal fun historical(at: Instant) = Observation(value, checkedAt, minOf(freshUntil, at), validUntil)
    override fun equals(other: Any?) = other is Observation<*> && value == other.value && checkedAt == other.checkedAt &&
        freshUntil == other.freshUntil && validUntil == other.validUntil
    override fun hashCode() = listOf(value, checkedAt, freshUntil, validUntil).hashCode()
}

@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("type")
@Serializable
sealed interface Check<out V : Any> {
    @Serializable @SerialName("not_checked") data object NotChecked : Check<Nothing>
    @Serializable @SerialName("observed")
    class Observed<out V : Any> internal constructor(val observation: Observation<V>) : Check<V> {
        override fun equals(other: Any?) = other is Observed<*> && observation == other.observation
        override fun hashCode() = observation.hashCode()
    }
    @Serializable @SerialName("failed")
    class Failed<out V : Any> internal constructor(
        val attemptedAt: Instant, val cause: CheckFailure, val previous: Observation<V>? = null,
    ) : Check<V> {
        override fun equals(other: Any?) = other is Failed<*> && attemptedAt == other.attemptedAt && cause == other.cause && previous == other.previous
        override fun hashCode() = listOf(attemptedAt, cause, previous).hashCode()
    }
    @Serializable @SerialName("conflict")
    class Conflict<out V : Any> internal constructor(val attemptedAt: Instant, val retained: Observation<V>) : Check<V> {
        override fun equals(other: Any?) = other is Conflict<*> && attemptedAt == other.attemptedAt && retained == other.retained
        override fun hashCode() = 31 * attemptedAt.hashCode() + retained.hashCode()
    }
}

fun <V : Any> Check<V>.lastObservation(): Observation<V>? = when (this) {
    Check.NotChecked -> null
    is Check.Observed -> observation
    is Check.Failed -> previous
    is Check.Conflict -> retained
}

@Serializable(with = CheckFailureSerializer::class)
sealed interface CheckFailure {
    @Serializable data class NotYetValid(val validFrom: Instant) : CheckFailure
    @Serializable data class RateLimited(val retryAt: Instant? = null) : CheckFailure
    @Serializable enum class Reason : CheckFailure {
        CREDENTIAL_EXPIRED, TRANSPORT, TIMEOUT, REFERENCE_UNAUTHENTICATED, UNSUPPORTED_PROOF,
        INVALID_SIGNATURE, UNAUTHORIZED_SIGNER, INVALID_VALIDITY, INVALID_LIST, RESOURCE_LIMIT,
    }
}

val CheckFailure.code: String get() = when (this) {
    is CheckFailure.NotYetValid -> "NOT_YET_VALID"
    is CheckFailure.RateLimited -> "RATE_LIMITED"
    is CheckFailure.Reason -> name
}

@Serializable
enum class ReferenceProblem { MALFORMED, RESOURCE_LIMIT, CONFLICTING_REFERENCE }
@Serializable
enum class DiscoveryProblem { MALFORMED_CONTAINER, RESOURCE_LIMIT }

/** Only the resolver creates/rebinds an entry; wallet updates preserve its owner and value type. */
@Serializable(with = CredentialStatusEntrySerializer::class)
sealed class CredentialStatusEntry {
    abstract val referenceId: String

    @Serializable
    class Token internal constructor(override val referenceId: String, val check: Check<TokenStatus>) : CredentialStatusEntry() {
        init { require(referenceId.isNotBlank()); require(check !is Check.Conflict || irreversible) }
    }
    @Serializable
    class Mso internal constructor(override val referenceId: String, val check: Check<MsoStatus>) : CredentialStatusEntry() {
        init { require(referenceId.isNotBlank()); require(check !is Check.Conflict || irreversible) }
    }
    @Serializable
    class Bitstring internal constructor(override val referenceId: String, val purpose: BitstringPurpose, val check: Check<UByte>) : CredentialStatusEntry() {
        init { require(referenceId.isNotBlank()); require(check !is Check.Conflict || irreversible) }
    }
    @Serializable
    class Legacy internal constructor(override val referenceId: String, val purpose: LegacyPurpose, val check: Check<Boolean>) : CredentialStatusEntry() {
        init { require(referenceId.isNotBlank()); require(check !is Check.Conflict || irreversible) }
    }
    @Serializable
    class Unsupported internal constructor(override val referenceId: String, val method: String) : CredentialStatusEntry() {
        init { require(referenceId.isNotBlank() && method.isNotBlank()) }
    }
    @Serializable
    class Malformed internal constructor(override val referenceId: String, val problem: ReferenceProblem) : CredentialStatusEntry() {
        init { require(referenceId.isNotBlank()) }
    }

    final override fun equals(other: Any?): Boolean = other is CredentialStatusEntry && referenceId == other.referenceId && when (this) {
        is Token -> other is Token && check == other.check
        is Mso -> other is Mso && check == other.check
        is Bitstring -> other is Bitstring && purpose == other.purpose && check == other.check
        is Legacy -> other is Legacy && purpose == other.purpose && check == other.check
        is Unsupported -> other is Unsupported && method == other.method
        is Malformed -> other is Malformed && problem == other.problem
    }
    final override fun hashCode(): Int = 31 * referenceId.hashCode() + when (this) {
        is Token -> check.hashCode()
        is Mso -> check.hashCode()
        is Bitstring -> 31 * purpose.hashCode() + check.hashCode()
        is Legacy -> 31 * purpose.hashCode() + check.hashCode()
        is Unsupported -> method.hashCode()
        is Malformed -> problem.hashCode()
    }
}

/** Successful empty discovery is absence, never clear; a broken container is a separate result. */
@Serializable(with = CredentialStatusSerializer::class)
sealed interface CredentialStatus {
    class References internal constructor(entries: List<CredentialStatusEntry>) : CredentialStatus {
        val entries: List<CredentialStatusEntry> = StatusListSnapshot(entries)
        init { require(this.entries.map { it.referenceId }.distinct().size == this.entries.size) }
        override fun equals(other: Any?) = other is References && entries == other.entries
        override fun hashCode() = entries.hashCode()
    }
    @Serializable
    data class DiscoveryFailed(val problem: DiscoveryProblem) : CredentialStatus

    companion object { fun references(entries: List<CredentialStatusEntry> = emptyList()): CredentialStatus = References(entries) }
}

/** Read-only enumeration for consumers; discovery failure is still preserved by the owning status. */
val CredentialStatus.entries: List<CredentialStatusEntry> get() = when (this) {
    is CredentialStatus.References -> entries
    is CredentialStatus.DiscoveryFailed -> emptyList()
}

/** Stable failure category, without exception text or remote payloads in the domain model. */
class CredentialStatusException(val reason: CheckFailure, cause: Throwable? = null) : Exception(reason.code, cause)

/** A detached read-only collection: downcasts cannot mutate the validated snapshot. */
internal class StatusListSnapshot<T>(values: Collection<T>) : AbstractList<T>() {
    private val values = values.toList()
    override val size: Int get() = values.size
    override fun get(index: Int): T = values[index]
}
