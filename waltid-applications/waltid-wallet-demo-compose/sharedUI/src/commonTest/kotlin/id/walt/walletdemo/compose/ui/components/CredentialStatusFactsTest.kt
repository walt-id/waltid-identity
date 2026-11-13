package id.walt.walletdemo.compose.ui.components

import id.walt.walletdemo.compose.logic.WalletDemoCredentialStatus
import id.walt.walletdemo.compose.logic.WalletDemoStatusReference
import id.walt.walletdemo.compose.ui.components.CredentialStatusFact.TimeKind
import id.walt.walletdemo.compose.ui.resources.Res
import id.walt.walletdemo.compose.ui.resources.credential_status_conflict_detail
import id.walt.walletdemo.compose.ui.resources.credential_status_conflicting_update
import id.walt.walletdemo.compose.ui.resources.credential_status_latest_check_failed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class CredentialStatusFactsTest {
    private val checkedAt = "2026-11-13T09:00:00.123456789Z"
    private val later = "2026-11-13T10:00:00Z"
    private val reference = WalletDemoStatusReference("first", protocolName = "BITSTRING_STATUS_LIST", purpose = "revocation",
        result = "CLEAR", checkedAt = checkedAt, freshUntil = later, validUntil = later, attemptedAt = checkedAt, freshness = "FRESH")
    private val clear = WalletDemoCredentialStatus("NOT_REVOKED", "NONE", "FRESH", "CHECK", "opaque", "COMPLETE",
        lastCheckedAt = checkedAt, lastAttemptAt = checkedAt, purposes = listOf("revocation"), references = listOf(reference))

    @Test
    fun simpleResultsAreFlatWithTheirScopeAndOriginalTimeShownOnce() {
        val noStatus = WalletDemoCredentialStatus("NOT_PROVIDED", "NONE", "UNKNOWN", "NONE", "none")
        val unchecked = clear.copy(kind = "NOT_CHECKED", freshness = "UNKNOWN", coverage = "NONE", lastCheckedAt = null,
            lastAttemptAt = null, purposes = emptyList(), references = listOf(reference.copy(result = null, checkedAt = null,
                freshUntil = null, validUntil = null, attemptedAt = null, freshness = "UNKNOWN")))
        val token = reference.copy(protocolName = "TOKEN_STATUS_LIST", purpose = "token")
        val suspension = reference.copy(purpose = "suspension")
        val simple = listOf(noStatus, unchecked, clear,
            clear.copy(kind = "NOT_SUSPENDED", purposes = listOf("suspension"), references = listOf(suspension)),
            clear.copy(kind = "CHECKED", purposes = listOf("token"), references = listOf(token)),
            clear.copy(kind = "REVOKED", issue = "REVOKED", references = listOf(reference.copy(result = "REVOKED"))),
            clear.copy(kind = "INVALID", issue = "INVALID", purposes = listOf("token"), references = listOf(token.copy(result = "INVALID"))),
            clear.copy(kind = "SUSPENDED", issue = "SUSPENDED", purposes = listOf("suspension"), references = listOf(suspension.copy(result = "SUSPENDED"))))
        simple.forEach { assertTrue(it.facts().extra.isEmpty(), "A simple ${it.kind} must not acquire an empty or repetitive disclosure") }
        val visible = clear.facts().main
        assertEquals(listOf("revocation"), visible.filterIsInstance<CredentialStatusFact.Scope>().single().purposes)
        assertEquals(listOf(CredentialStatusFact.Time(TimeKind.LAST_CHECKED, Instant.parse(checkedAt))), visible.filterIsInstance<CredentialStatusFact.Time>())
        assertTrue(noStatus.facts().main.isEmpty())
        assertTrue(unchecked.facts().main.isEmpty())
    }

    @Test
    fun differentReferenceTimesRemainSeparateEvenWhenPurposesAndResultsMatch() {
        val second = reference.copy(referenceId = "second", checkedAt = later, attemptedAt = later,
            freshUntil = "2026-11-13T11:00:00Z", validUntil = "2026-11-13T11:00:00Z")
        val facts = clear.copy(lastAttemptAt = later, references = listOf(reference, second)).facts()
        assertTrue(facts.main.none { it is CredentialStatusFact.Time }, "Do not label the oldest aggregate time as the last check")
        assertEquals(listOf("first", "second"), facts.extra.map { it.id })
        assertEquals(listOf(Instant.parse(checkedAt), Instant.parse(later)), facts.extra.map {
            it.facts.filterIsInstance<CredentialStatusFact.Time>().single().at
        })
        assertEquals(2, facts.extra.flatMap { it.facts }.filterIsInstance<CredentialStatusFact.Outcome>().size)
    }

    @Test
    fun aFailedRefreshAcknowledgesFailureAndKeepsAnEqualTimeAttemptDistinctFromSuccess() {
        val facts = clear.copy(action = "RETRY", failure = "TRANSPORT",
            references = listOf(reference.copy(failure = "TRANSPORT"))).facts()
        assertTrue(CredentialStatusFact.Note(Res.string.credential_status_latest_check_failed) in facts.main)
        assertEquals(TimeKind.LAST_CHECKED, facts.main.filterIsInstance<CredentialStatusFact.Time>().single().kind)
        val extra = facts.extra.single().facts
        val attempt = extra.filterIsInstance<CredentialStatusFact.Time>().single()
        assertEquals(TimeKind.LATEST_ATTEMPT, attempt.kind)
        assertEquals(Instant.parse(checkedAt), attempt.at, "Equal clock readings are still distinct success and failure events")
        assertFalse(extra.any { it is CredentialStatusFact.Outcome }, "The primary clear result and scope are already visible")
    }

    @Test
    fun temporaryIneligibilityShowsItsReasonAndRecoveryTimeWithoutInventingASuccess() {
        for ((reason, timeKind) in listOf("NOT_YET_VALID" to TimeKind.VALID_FROM, "RATE_LIMITED" to TimeKind.RETRY_FROM)) {
            val pending = reference.copy(result = null, checkedAt = null, freshUntil = null, validUntil = null,
                failure = reason, eligibleAt = later, freshness = "UNKNOWN")
            val facts = clear.copy(kind = "UNAVAILABLE", issue = "UNCERTAIN", freshness = "UNKNOWN", action = "NONE",
                coverage = "NONE", lastCheckedAt = null, purposes = emptyList(), failure = reason, references = listOf(pending)).facts()
            assertTrue(facts.extra.isEmpty())
            assertEquals(setOf(TimeKind.LATEST_ATTEMPT, timeKind), facts.main.filterIsInstance<CredentialStatusFact.Time>().map { it.kind }.toSet())
            assertEquals(Instant.parse(later), facts.main.filterIsInstance<CredentialStatusFact.Time>().single { it.kind == timeKind }.at)
            assertTrue(facts.main.any { it is CredentialStatusFact.Note })
        }
    }

    @Test
    fun aConflictKeepsItsWarningVisibleAndExplainsTheRetainedResultOnlyOnce() {
        val facts = clear.copy(kind = "REVOKED", issue = "REVOKED", action = "RETRY", coverage = "PARTIAL",
            references = listOf(reference.copy(result = "REVOKED", conflict = true))).facts()
        assertTrue(CredentialStatusFact.Note(Res.string.credential_status_conflicting_update) in facts.main)
        val extra = facts.extra.single().facts
        assertEquals(1, extra.count { it == CredentialStatusFact.Note(Res.string.credential_status_conflict_detail) })
        assertEquals(TimeKind.LATEST_ATTEMPT, extra.filterIsInstance<CredentialStatusFact.Time>().single().kind)
        assertFalse(extra.any { it is CredentialStatusFact.Outcome })
    }

    @Test
    fun unreadableAndUnsupportedStatusShowsExplanationWithoutFakeDatesOrReferenceFacts() {
        val unavailable = WalletDemoCredentialStatus("UNAVAILABLE", "UNCERTAIN", "UNKNOWN", "NONE", "unreadable",
            discoveryProblem = "RESOURCE_LIMIT")
        val unsupported = unavailable.copy(kind = "UNSUPPORTED", issue = "UNSUPPORTED", discoveryProblem = null,
            references = listOf(WalletDemoStatusReference("unsupported", method = "PrivateMethod")))
        for (status in listOf(unavailable, unsupported, WalletDemoCredentialStatus.ServerUnsupported)) {
            val facts = status.facts()
            assertTrue(facts.main.any { it is CredentialStatusFact.Note })
            assertFalse(facts.main.any { it is CredentialStatusFact.Time || it is CredentialStatusFact.Outcome })
            assertTrue(facts.extra.isEmpty())
        }
    }
}
