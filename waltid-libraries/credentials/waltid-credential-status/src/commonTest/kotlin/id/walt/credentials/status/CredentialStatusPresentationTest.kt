package id.walt.credentials.status

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class CredentialStatusPresentationTest {
    private val checked = Instant.fromEpochSeconds(1_800_000_000)
    private fun observation(value: UInt = 0u, purpose: String = "revocation", id: String = "ref") =
        StatusObservation(id, StatusProtocol.BITSTRING_STATUS_LIST, purpose, value, checked, checked + 30.seconds)

    @Test
    fun homeIsQuietForAbsenceUncheckedAndFreshClearButKeepsKnownAdverseResults() {
        val at = checked + 1.seconds
        val absent = CredentialStatusMetadata().presentation(at)
        assertEquals(CredentialStatusIssue.NONE, absent.issue)
        assertEquals(CredentialStatusKind.NOT_PROVIDED, absent.kind)
        assertEquals(CredentialStatusCoverage.NONE, absent.coverage)
        assertEquals(CredentialStatusIssue.NONE, CredentialStatusMetadata(listOf(
            CredentialStatusEntry.NotChecked("ref", StatusProtocol.BITSTRING_STATUS_LIST, "revocation"),
        )).presentation(at).issue)
        assertEquals(CredentialStatusIssue.NONE, CredentialStatusMetadata(listOf(
            CredentialStatusEntry.Observed(observation()),
        )).presentation(at).issue)
        val adverse = CredentialStatusMetadata(listOf(CredentialStatusEntry.Unavailable(
            "ref", StatusFailure.TRANSPORT, checked + 40.seconds, observation(1u),
        ))).presentation(checked + 40.seconds)
        assertEquals(CredentialStatusKind.REVOKED, adverse.kind)
        assertEquals(CredentialStatusIssue.REVOKED, adverse.issue)
        assertEquals(CredentialStatusFreshness.STALE, adverse.freshness)
        assertEquals(checked, adverse.lastCheckedAt)
    }

    @Test
    fun freshNetworkFailureIsFeedbackWhileUnverifiedOrPartialCoverageNeedsAttention() {
        val previous = observation()
        val transient = CredentialStatusMetadata(listOf(CredentialStatusEntry.Unavailable(
            "ref", StatusFailure.TRANSPORT, checked + 1.seconds, previous,
        ))).presentation(checked + 1.seconds)
        assertEquals(CredentialStatusIssue.NONE, transient.issue)
        val unverified = CredentialStatusMetadata(listOf(CredentialStatusEntry.Unavailable(
            "ref", StatusFailure.INVALID_SIGNATURE, checked + 1.seconds, previous,
        ))).presentation(checked + 1.seconds)
        assertEquals(CredentialStatusKind.UNVERIFIED, unverified.kind)
        val partial = CredentialStatusMetadata(listOf(CredentialStatusEntry.Observed(previous),
            CredentialStatusEntry.Unsupported("other", "identifier_list"))).presentation(checked + 1.seconds)
        assertEquals(CredentialStatusKind.PARTIAL, partial.kind)
        assertEquals(CredentialStatusCoverage.PARTIAL, partial.coverage)
    }

    @Test
    fun advisoryRevisionIgnoresCheckTimesButChangesAtFreshnessAndSeverityBoundaries() {
        val old = CredentialStatusMetadata(listOf(CredentialStatusEntry.Observed(observation())))
        val updated = CredentialStatusMetadata(listOf(CredentialStatusEntry.Observed(observation().copy(
            checkedAt = checked + 1.seconds, freshUntil = checked + 31.seconds,
        ))))
        assertEquals(old.presentation(checked + 2.seconds).advisoryRevision, updated.presentation(checked + 2.seconds).advisoryRevision)
        assertNotEquals(old.presentation(checked + 2.seconds).advisoryRevision, old.presentation(checked + 30.seconds).advisoryRevision)
        val revoked = CredentialStatusMetadata(listOf(CredentialStatusEntry.Observed(observation(1u))))
        assertNotEquals(old.presentation(checked + 2.seconds).advisoryRevision, revoked.presentation(checked + 2.seconds).advisoryRevision)
    }
}
