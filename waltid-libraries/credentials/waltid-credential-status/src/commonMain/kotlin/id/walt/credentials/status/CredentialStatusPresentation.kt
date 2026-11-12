package id.walt.credentials.status

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
enum class CredentialStatusKind {
    NOT_PROVIDED, NOT_CHECKED, NOT_REVOKED, NOT_SUSPENDED, CHECKED,
    REVOKED, INVALID, SUSPENDED, OUT_OF_DATE, UNAVAILABLE, UNVERIFIED,
    UNSUPPORTED, PARTIAL, NEEDS_REVIEW,
}

@Serializable
enum class CredentialStatusIssue { NONE, REVOKED, INVALID, SUSPENDED, OUT_OF_DATE, UNCERTAIN, UNSUPPORTED, NEEDS_REVIEW }

@Serializable
enum class CredentialStatusFreshness { UNKNOWN, FRESH, STALE }

@Serializable
enum class CredentialStatusRefreshAction { NONE, CHECK, RETRY }

/**
 * Shared semantic mapping for Kotlin, Swift and browser clients. Renderers localize these codes;
 * they never reinterpret protocol bits or implement a second freshness/aggregation engine.
 */
@Serializable
data class CredentialStatusPresentation(
    val kind: CredentialStatusKind,
    val issue: CredentialStatusIssue,
    val freshness: CredentialStatusFreshness,
    val action: CredentialStatusRefreshAction,
    val lastCheckedAt: Instant? = null,
    val lastAttemptAt: Instant? = null,
    val failure: StatusFailure? = null,
    val purposes: List<String> = emptyList(),
    val coverage: CredentialStatusCoverage = CredentialStatusCoverage.NONE,
) {
    /** Timestamp-only changes do not invalidate a rendered consent warning. */
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @kotlinx.serialization.EncodeDefault
    val advisoryRevision: String = "$kind|$issue|$freshness|$coverage|${purposes.joinToString(",")}"
}

@Serializable
enum class CredentialStatusCoverage { NONE, COMPLETE, PARTIAL }

fun CredentialStatusMetadata.presentation(at: Instant): CredentialStatusPresentation {
    val observations = entries.mapNotNull(CredentialStatusEntry::lastObservation)
    val failures = entries.filterIsInstance<CredentialStatusEntry.Unavailable>()
    val unsupported = entries.any { it is CredentialStatusEntry.Unsupported ||
        it is CredentialStatusEntry.Unavailable && it.reason in setOf(StatusFailure.UNSUPPORTED_METHOD, StatusFailure.UNSUPPORTED_PROOF) }
    val unverified = failures.any { it.reason in setOf(StatusFailure.INVALID_SIGNATURE, StatusFailure.REFERENCE_UNAUTHENTICATED,
        StatusFailure.UNAUTHORIZED_SIGNER, StatusFailure.INVALID_LIST, StatusFailure.INVALID_VALIDITY) }
    val adverse = observations.firstOrNull { it.meaning == StatusMeaning.REVOKED }
        ?: observations.firstOrNull { it.meaning == StatusMeaning.INVALID }
        ?: observations.firstOrNull { it.meaning == StatusMeaning.SUSPENDED }
    val fresh = observations.isNotEmpty() && observations.all { it.isFresh(at) }
    val complete = entries.isNotEmpty() && observations.size == entries.size && !unsupported && !unverified
    val purposes = observations.map(StatusObservation::purpose).distinct().sorted()
    val kind = when {
        adverse?.meaning == StatusMeaning.REVOKED -> CredentialStatusKind.REVOKED
        adverse?.meaning == StatusMeaning.INVALID -> CredentialStatusKind.INVALID
        adverse?.meaning == StatusMeaning.SUSPENDED -> CredentialStatusKind.SUSPENDED
        entries.isEmpty() -> CredentialStatusKind.NOT_PROVIDED
        unverified -> CredentialStatusKind.UNVERIFIED
        observations.isNotEmpty() && !fresh -> CredentialStatusKind.OUT_OF_DATE
        observations.isNotEmpty() && !complete -> CredentialStatusKind.PARTIAL
        observations.any { it.meaning != StatusMeaning.CLEAR } -> CredentialStatusKind.NEEDS_REVIEW
        complete && observations.all { it.purpose == "suspension" } -> CredentialStatusKind.NOT_SUSPENDED
        complete && observations.all { it.purpose in setOf("revocation", "token") } -> CredentialStatusKind.NOT_REVOKED
        complete -> CredentialStatusKind.CHECKED
        unsupported -> CredentialStatusKind.UNSUPPORTED
        failures.isNotEmpty() -> CredentialStatusKind.UNAVAILABLE
        else -> CredentialStatusKind.NOT_CHECKED
    }
    val issue = when (kind) {
        CredentialStatusKind.REVOKED -> CredentialStatusIssue.REVOKED
        CredentialStatusKind.INVALID -> CredentialStatusIssue.INVALID
        CredentialStatusKind.SUSPENDED -> CredentialStatusIssue.SUSPENDED
        CredentialStatusKind.OUT_OF_DATE -> CredentialStatusIssue.OUT_OF_DATE
        CredentialStatusKind.UNSUPPORTED -> CredentialStatusIssue.UNSUPPORTED
        CredentialStatusKind.UNAVAILABLE, CredentialStatusKind.UNVERIFIED, CredentialStatusKind.PARTIAL -> CredentialStatusIssue.UNCERTAIN
        CredentialStatusKind.NEEDS_REVIEW -> CredentialStatusIssue.NEEDS_REVIEW
        else -> CredentialStatusIssue.NONE
    }
    val checkable = entries.any { entry -> when (entry) {
        is CredentialStatusEntry.NotChecked, is CredentialStatusEntry.Observed -> true
        is CredentialStatusEntry.Unsupported -> false
        is CredentialStatusEntry.Unavailable -> entry.reason !in setOf(StatusFailure.UNSUPPORTED_METHOD,
            StatusFailure.UNSUPPORTED_PROOF, StatusFailure.REFERENCE_MALFORMED, StatusFailure.CREDENTIAL_EXPIRED, StatusFailure.DISABLED)
    } }
    return CredentialStatusPresentation(kind, issue,
        if (observations.isEmpty()) CredentialStatusFreshness.UNKNOWN else if (fresh) CredentialStatusFreshness.FRESH else CredentialStatusFreshness.STALE,
        if (!checkable) CredentialStatusRefreshAction.NONE else if (failures.isNotEmpty()) CredentialStatusRefreshAction.RETRY else CredentialStatusRefreshAction.CHECK,
        observations.minOfOrNull(StatusObservation::checkedAt), failures.maxOfOrNull(CredentialStatusEntry.Unavailable::attemptedAt),
        failures.maxByOrNull(CredentialStatusEntry.Unavailable::attemptedAt)?.reason, purposes,
        if (complete) CredentialStatusCoverage.COMPLETE else if (observations.isNotEmpty()) CredentialStatusCoverage.PARTIAL else CredentialStatusCoverage.NONE)
}
