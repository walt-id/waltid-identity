package id.walt.walletdemo.compose.ui.components

import id.walt.walletdemo.compose.logic.WalletDemoCredentialStatus
import id.walt.walletdemo.compose.logic.WalletDemoStatusReference
import id.walt.walletdemo.compose.ui.resources.Res
import id.walt.walletdemo.compose.ui.resources.credential_status_ambiguous_detail
import id.walt.walletdemo.compose.ui.resources.credential_status_conflict_detail
import id.walt.walletdemo.compose.ui.resources.credential_status_conflicting_reference_detail
import id.walt.walletdemo.compose.ui.resources.credential_status_conflicting_update
import id.walt.walletdemo.compose.ui.resources.credential_status_credential_time_detail
import id.walt.walletdemo.compose.ui.resources.credential_status_custom_value
import id.walt.walletdemo.compose.ui.resources.credential_status_discovery_failed_detail
import id.walt.walletdemo.compose.ui.resources.credential_status_latest_check_failed
import id.walt.walletdemo.compose.ui.resources.credential_status_limit_detail
import id.walt.walletdemo.compose.ui.resources.credential_status_not_yet_valid_detail
import id.walt.walletdemo.compose.ui.resources.credential_status_partial
import id.walt.walletdemo.compose.ui.resources.credential_status_rate_limited_detail
import id.walt.walletdemo.compose.ui.resources.credential_status_read_unavailable
import id.walt.walletdemo.compose.ui.resources.credential_status_reference_auth_detail
import id.walt.walletdemo.compose.ui.resources.credential_status_reference_invalid_detail
import id.walt.walletdemo.compose.ui.resources.credential_status_reserved_value
import id.walt.walletdemo.compose.ui.resources.credential_status_server_unsupported_detail
import id.walt.walletdemo.compose.ui.resources.credential_status_stale
import id.walt.walletdemo.compose.ui.resources.credential_status_transport_detail
import id.walt.walletdemo.compose.ui.resources.credential_status_unsupported_detail
import id.walt.walletdemo.compose.ui.resources.credential_status_unverified_detail
import id.walt.walletdemo.compose.ui.resources.credential_status_update_available
import org.jetbrains.compose.resources.StringResource
import kotlin.time.Instant

/** Layout facts only. The producer already owns status, eligibility, coverage and freshness. */
internal sealed interface CredentialStatusFact {
    data class Scope(val purposes: List<String>) : CredentialStatusFact
    data class Time(val kind: TimeKind, val at: Instant, val id: String = kind.tag) : CredentialStatusFact
    data class Note(val text: StringResource) : CredentialStatusFact
    data class Outcome(val reference: WalletDemoStatusReference) : CredentialStatusFact

    enum class TimeKind(val tag: String) {
        LAST_CHECKED("last-checked"), LATEST_ATTEMPT("latest-attempt"), VALID_FROM("valid-from"), RETRY_FROM("retry-from"),
    }
}

internal data class CredentialStatusFactGroup(val id: String, val facts: List<CredentialStatusFact>)
internal data class CredentialStatusFacts(val main: List<CredentialStatusFact>, val extra: List<CredentialStatusFactGroup>)

/** Prepare once per surface; disclosure follows the actual remaining facts, never a status-kind allowlist. */
internal fun WalletDemoCredentialStatus.facts(readUnavailable: Boolean = false): CredentialStatusFacts {
    val observedTimes = references.mapNotNull { it.checkedAt.instantOrNull() }.distinct()
    val checked = observedTimes.singleOrNull() ?: lastCheckedAt.instantOrNull().takeIf { observedTimes.isEmpty() && kind != "NOT_PROVIDED" }
    val observed = checked != null || references.any { it.checkedAt != null }
    val attemptTimes = references.mapNotNull { it.attemptedAt.instantOrNull() }.distinct()
    val attempted = if (observed) null else attemptTimes.singleOrNull() ?: lastAttemptAt.instantOrNull().takeIf { attemptTimes.isEmpty() }
    val recovery = references.mapNotNull { it.recoveryTime() }.distinctBy { it.kind to it.at }
        .singleOrNull()?.takeIf { action == "NONE" }
    val single = references.singleOrNull()
    val cause = single?.failure ?: failure
    val mainCause = cause?.takeIf {
        if (references.size > 1) action == "NONE"
        else !observed || it in setOf("CREDENTIAL_EXPIRED", "NOT_YET_VALID")
    }
    val main = buildList {
        if (positive && purposes.isNotEmpty()) add(CredentialStatusFact.Scope(purposes))
        checked?.let { add(CredentialStatusFact.Time(CredentialStatusFact.TimeKind.LAST_CHECKED, it)) }
        attempted?.let { add(CredentialStatusFact.Time(CredentialStatusFact.TimeKind.LATEST_ATTEMPT, it)) }
        if (readUnavailable) add(CredentialStatusFact.Note(Res.string.credential_status_read_unavailable))
        if (freshness == "STALE" && kind != "OUT_OF_DATE") add(CredentialStatusFact.Note(Res.string.credential_status_stale))
        if (coverage == "PARTIAL" && kind != "PARTIAL") add(CredentialStatusFact.Note(Res.string.credential_status_partial))
        if (references.any { it.conflict }) add(CredentialStatusFact.Note(Res.string.credential_status_conflicting_update))
        else if (observed && mainCause == null && (failure != null || references.any { it.failure != null }))
            add(CredentialStatusFact.Note(Res.string.credential_status_latest_check_failed))
        mainCause?.let { add(CredentialStatusFact.Note(statusFailureExplanation(it))) }
        discoveryProblem?.let { add(CredentialStatusFact.Note(if (it == "RESOURCE_LIMIT") Res.string.credential_status_limit_detail
            else Res.string.credential_status_discovery_failed_detail)) }
        single?.problem?.let { add(CredentialStatusFact.Note(statusReferenceProblem(it))) }
        if (single?.method != null && mainCause == null) add(CredentialStatusFact.Note(Res.string.credential_status_unsupported_detail))
        when (single?.result) {
            "APPLICATION_SPECIFIC" -> add(CredentialStatusFact.Note(Res.string.credential_status_custom_value))
            "RESERVED" -> add(CredentialStatusFact.Note(Res.string.credential_status_reserved_value))
            "REFRESH" -> add(CredentialStatusFact.Note(Res.string.credential_status_update_available))
        }
        if (kind == "SERVER_UNSUPPORTED") add(CredentialStatusFact.Note(Res.string.credential_status_server_unsupported_detail))
        recovery?.let(::add)
    }.distinct()
    val extra = references.mapNotNull { reference ->
        val facts = buildList {
            if (references.size > 1) {
                add(CredentialStatusFact.Outcome(reference))
                reference.checkedAt.instantOrNull()?.takeIf { it != checked }?.let {
                    add(CredentialStatusFact.Time(CredentialStatusFact.TimeKind.LAST_CHECKED, it, "${reference.referenceId}-last-checked"))
                }
                reference.problem?.let { add(CredentialStatusFact.Note(statusReferenceProblem(it))) }
            }
            reference.failure?.takeIf { references.size > 1 || it != mainCause }?.let {
                add(CredentialStatusFact.Note(statusFailureExplanation(it)))
            }
            val distinctAttempt = reference.failure != null || reference.conflict || reference.attemptedAt != reference.checkedAt
            if (distinctAttempt) reference.attemptedAt.instantOrNull()?.takeIf { it != attempted }?.let {
                add(CredentialStatusFact.Time(CredentialStatusFact.TimeKind.LATEST_ATTEMPT, it,
                    if (references.size == 1) "latest-attempt" else "${reference.referenceId}-latest-attempt"))
            }
            if (reference.conflict) add(CredentialStatusFact.Note(Res.string.credential_status_conflict_detail))
            reference.recoveryTime()?.takeUnless { recovery != null && it.kind == recovery.kind && it.at == recovery.at }?.let(::add)
        }.distinct()
        facts.takeIf { it.isNotEmpty() }?.let { CredentialStatusFactGroup(reference.referenceId, it) }
    }.toMutableList()
    if (references.isEmpty() && observed && cause != null) {
        val facts = buildList {
            if (cause != mainCause) add(CredentialStatusFact.Note(statusFailureExplanation(cause)))
            lastAttemptAt.instantOrNull()?.let { add(CredentialStatusFact.Time(CredentialStatusFact.TimeKind.LATEST_ATTEMPT, it)) }
        }
        if (facts.isNotEmpty()) extra += CredentialStatusFactGroup("latest-attempt", facts)
    }
    return CredentialStatusFacts(main, extra)
}

private fun WalletDemoStatusReference.recoveryTime(): CredentialStatusFact.Time? = eligibleAt.instantOrNull()?.let {
    CredentialStatusFact.Time(if (failure == "NOT_YET_VALID") CredentialStatusFact.TimeKind.VALID_FROM
        else CredentialStatusFact.TimeKind.RETRY_FROM, it, "$referenceId-eligible-at")
}

private fun String?.instantOrNull(): Instant? = this?.let { runCatching { Instant.parse(it) }.getOrNull() }

private fun statusReferenceProblem(problem: String): StringResource = when (problem) {
    "RESOURCE_LIMIT" -> Res.string.credential_status_limit_detail
    "CONFLICTING_REFERENCE" -> Res.string.credential_status_conflicting_reference_detail
    else -> Res.string.credential_status_reference_invalid_detail
}

private fun statusFailureExplanation(reason: String): StringResource = when (reason) {
    "CREDENTIAL_EXPIRED" -> Res.string.credential_status_credential_time_detail
    "NOT_YET_VALID" -> Res.string.credential_status_not_yet_valid_detail
    "REFERENCE_UNAUTHENTICATED" -> Res.string.credential_status_reference_auth_detail
    "REFERENCE_MALFORMED" -> Res.string.credential_status_reference_invalid_detail
    "RATE_LIMITED" -> Res.string.credential_status_rate_limited_detail
    "RESOURCE_LIMIT" -> Res.string.credential_status_limit_detail
    "AMBIGUOUS_CREDENTIAL_ID" -> Res.string.credential_status_ambiguous_detail
    "CONFLICTING_IRREVERSIBLE_STATUS" -> Res.string.credential_status_conflict_detail
    "UNSUPPORTED_METHOD", "UNSUPPORTED_PROOF" -> Res.string.credential_status_unsupported_detail
    "INVALID_SIGNATURE", "UNAUTHORIZED_SIGNER", "INVALID_LIST", "INVALID_VALIDITY" -> Res.string.credential_status_unverified_detail
    else -> Res.string.credential_status_transport_detail
}
