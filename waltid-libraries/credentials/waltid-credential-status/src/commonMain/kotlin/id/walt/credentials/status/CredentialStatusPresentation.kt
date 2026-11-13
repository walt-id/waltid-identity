package id.walt.credentials.status

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.kotlincrypto.hash.sha2.SHA256
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
@Serializable
enum class CredentialStatusCoverage { NONE, COMPLETE, PARTIAL }

/** A fixed-time assessment owns the derivation; no caller can supply a competing presentation. */
class StatusAssessment private constructor(val status: CredentialStatus, val evaluatedAt: Instant) {
    val presentation: CredentialStatusPresentation = CredentialStatusPresentation.derive(status, evaluatedAt)
    companion object { internal fun create(status: CredentialStatus, at: Instant) = StatusAssessment(status, at) }
}
fun CredentialStatus.assess(at: Instant): StatusAssessment = StatusAssessment.create(this, at)
fun CredentialStatus.presentation(at: Instant): CredentialStatusPresentation = assess(at).presentation

/** Immutable domain projection. Decoded wire data never becomes an authoritative assessment. */
class CredentialStatusPresentation private constructor(
    val kind: CredentialStatusKind,
    val issue: CredentialStatusIssue,
    val freshness: CredentialStatusFreshness,
    val action: CredentialStatusRefreshAction,
    val lastCheckedAt: Instant?,
    val lastAttemptAt: Instant?,
    val failure: CheckFailure?,
    purposes: List<String>,
    val coverage: CredentialStatusCoverage,
    references: List<CredentialStatusReferencePresentation>,
    val advisoryRevision: String,
    val lastKnown: Boolean,
    val discoveryProblem: DiscoveryProblem?,
) {
    val purposes: List<String> = StatusListSnapshot(purposes)
    val references: List<CredentialStatusReferencePresentation> = StatusListSnapshot(references)

    fun toData(): CredentialStatusPresentationData = CredentialStatusPresentationData(
        kind, issue, freshness, action, advisoryRevision, lastKnown, lastCheckedAt, lastAttemptAt, failure,
        purposes, coverage, references, discoveryProblem,
    )
    override fun equals(other: Any?) = other is CredentialStatusPresentation && toData() == other.toData()
    override fun hashCode() = toData().hashCode()

    companion object {
        internal fun derive(status: CredentialStatus, at: Instant): CredentialStatusPresentation {
            val entries = status.entries
            val observed = entries.filter { it.lastObservation() != null }
            val failures = entries.mapNotNull { it.failure }
            val unsupported = entries.any { it is CredentialStatusEntry.Unsupported || it.failure == CheckFailure.Reason.UNSUPPORTED_PROOF }
            val unverified = failures.any { it in verificationFailures }
            val broken = status is CredentialStatus.DiscoveryFailed || entries.any { it is CredentialStatusEntry.Malformed || it.hasConflict }
            fun primary(meaning: StatusMeaning) = observed.filter { it.meaning == meaning }.let { matches ->
                matches.firstOrNull { it.lastObservation()?.isFresh(at) == true } ?: matches.firstOrNull()
            }
            val adverse = primary(StatusMeaning.REVOKED) ?: primary(StatusMeaning.INVALID) ?: primary(StatusMeaning.SUSPENDED)
            val fresh = observed.isNotEmpty() && observed.all { it.lastObservation()?.isFresh(at) == true }
            val complete = entries.isNotEmpty() && observed.size == entries.size && !unsupported && !unverified && !broken
            val purposes = observed.mapNotNull { it.displayPurpose }.distinct().sorted()
            val kind = when {
                adverse?.meaning == StatusMeaning.REVOKED -> CredentialStatusKind.REVOKED
                adverse?.meaning == StatusMeaning.INVALID -> CredentialStatusKind.INVALID
                adverse?.meaning == StatusMeaning.SUSPENDED -> CredentialStatusKind.SUSPENDED
                status is CredentialStatus.DiscoveryFailed -> CredentialStatusKind.UNAVAILABLE
                entries.isEmpty() -> CredentialStatusKind.NOT_PROVIDED
                unverified -> CredentialStatusKind.UNVERIFIED
                observed.isNotEmpty() && !fresh -> CredentialStatusKind.OUT_OF_DATE
                observed.isNotEmpty() && !complete -> CredentialStatusKind.PARTIAL
                observed.any { it.meaning != StatusMeaning.CLEAR } -> CredentialStatusKind.NEEDS_REVIEW
                complete && observed.all { it.purposeName == "suspension" } -> CredentialStatusKind.NOT_SUSPENDED
                complete && observed.all { it.purposeName == "revocation" } -> CredentialStatusKind.NOT_REVOKED
                complete -> CredentialStatusKind.CHECKED
                unsupported -> CredentialStatusKind.UNSUPPORTED
                failures.isNotEmpty() || broken -> CredentialStatusKind.UNAVAILABLE
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
            val freshness = if (observed.isEmpty()) CredentialStatusFreshness.UNKNOWN else if (fresh) CredentialStatusFreshness.FRESH else CredentialStatusFreshness.STALE
            val coverage = if (complete) CredentialStatusCoverage.COMPLETE else if (observed.isNotEmpty()) CredentialStatusCoverage.PARTIAL else CredentialStatusCoverage.NONE
            val action = if (entries.none { it.isCheckable(at) }) CredentialStatusRefreshAction.NONE
                else if (failures.isNotEmpty() || entries.any { it.hasConflict }) CredentialStatusRefreshAction.RETRY else CredentialStatusRefreshAction.CHECK
            val references = entries.map { it.toPresentation(at) }
            val referenceSemantics = references.map { reference ->
                val warning = when {
                reference.conflict -> "conflict"
                reference.problem != null -> "malformed"
                reference.method != null -> "unsupported"
                reference.failure in verificationFailures -> "unverified"
                reference.failure == CheckFailure.Reason.UNSUPPORTED_PROOF -> "unsupported"
                reference.failure is CheckFailure.NotYetValid -> "not_yet_valid"
                reference.failure == CheckFailure.Reason.CREDENTIAL_EXPIRED -> "expired"
                else -> null
                }
                JsonArray(listOf(JsonPrimitive(reference.purpose), JsonPrimitive(reference.result?.name),
                    JsonPrimitive(reference.freshness.name), JsonPrimitive(warning)))
            }.sortedBy { it.toString() }
            val lastKnown = adverse?.lastObservation()?.isFresh(at) == false
            val discoveryProblem = (status as? CredentialStatus.DiscoveryFailed)?.problem
            val semantic = JsonArray(listOf(
                JsonPrimitive(kind.name), JsonPrimitive(issue.name), JsonPrimitive(freshness.name), JsonPrimitive(coverage.name), JsonPrimitive(lastKnown),
                JsonArray(purposes.map(::JsonPrimitive)), JsonArray(referenceSemantics),
                JsonPrimitive(discoveryProblem?.name),
            )).toString()
            val revision = SHA256().digest(semantic.encodeToByteArray()).toHexString()
            return CredentialStatusPresentation(kind, issue, freshness, action,
                observed.mapNotNull { it.lastObservation()?.checkedAt }.minOrNull(),
                entries.mapNotNull { it.lastAttemptAt }.maxOrNull(),
                entries.filter { it.failure != null }.maxByOrNull { requireNotNull(it.lastAttemptAt) }?.failure,
                purposes, coverage, references, revision, lastKnown, discoveryProblem)
        }
    }
}

/** Transport-only values. They are never restored as evidence or supplied to the assessment. */
@Serializable
data class CredentialStatusPresentationData(
    val kind: CredentialStatusKind,
    val issue: CredentialStatusIssue,
    val freshness: CredentialStatusFreshness,
    val action: CredentialStatusRefreshAction,
    val advisoryRevision: String,
    val lastKnown: Boolean,
    val lastCheckedAt: Instant? = null,
    val lastAttemptAt: Instant? = null,
    val failure: CheckFailure? = null,
    val purposes: List<String> = emptyList(),
    val coverage: CredentialStatusCoverage = CredentialStatusCoverage.NONE,
    val references: List<CredentialStatusReferencePresentation> = emptyList(),
    val discoveryProblem: DiscoveryProblem? = null,
)

/** Already interpreted facts for detail renderers; no scalar, locator, index or signed list. */
@Serializable
data class CredentialStatusReferencePresentation(
    val referenceId: String,
    val protocol: StatusProtocol? = null,
    val method: String? = null,
    val purpose: String? = null,
    val result: StatusMeaning? = null,
    val checkedAt: Instant? = null,
    val freshUntil: Instant? = null,
    val validUntil: Instant? = null,
    val attemptedAt: Instant? = null,
    val failure: CheckFailure? = null,
    val conflict: Boolean = false,
    val problem: ReferenceProblem? = null,
    val freshness: CredentialStatusFreshness = CredentialStatusFreshness.UNKNOWN,
)

private val verificationFailures = setOf(CheckFailure.Reason.INVALID_SIGNATURE, CheckFailure.Reason.REFERENCE_UNAUTHENTICATED,
    CheckFailure.Reason.UNAUTHORIZED_SIGNER, CheckFailure.Reason.INVALID_LIST, CheckFailure.Reason.INVALID_VALIDITY)

private fun CredentialStatusEntry.toPresentation(at: Instant): CredentialStatusReferencePresentation {
    val observation = lastObservation()
    return CredentialStatusReferencePresentation(referenceId, protocol,
        (this as? CredentialStatusEntry.Unsupported)?.method, displayPurpose, meaning,
        observation?.checkedAt, observation?.freshUntil, observation?.validUntil, lastAttemptAt, failure, hasConflict,
        (this as? CredentialStatusEntry.Malformed)?.problem,
        if (observation == null) CredentialStatusFreshness.UNKNOWN else if (observation.isFresh(at)) CredentialStatusFreshness.FRESH else CredentialStatusFreshness.STALE)
}

/** Bounded localization keys, preserving each protocol's purpose vocabulary. */
private val CredentialStatusEntry.displayPurpose: String? get() = when (this) {
    is CredentialStatusEntry.Token -> "token"
    is CredentialStatusEntry.Mso -> "revocation"
    is CredentialStatusEntry.Bitstring -> when (val value = purpose) {
        is BitstringPurpose.Known -> value.value
        is BitstringPurpose.Extension -> "other"
    }
    is CredentialStatusEntry.Legacy -> when (val value = purpose) {
        is LegacyPurpose.Known -> value.value
        is LegacyPurpose.Extension -> "other"
    }
    is CredentialStatusEntry.Unsupported, is CredentialStatusEntry.Malformed -> null
}
