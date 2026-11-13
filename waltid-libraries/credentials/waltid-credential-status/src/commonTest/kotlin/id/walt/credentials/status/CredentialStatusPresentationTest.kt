package id.walt.credentials.status

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class CredentialStatusPresentationTest {
    private val checked = Instant.fromEpochSeconds(1_800_000_000)
    private fun observation(value: UByte = 0u, at: Instant = checked) = Observation(value, at, at + 30.seconds)
    private fun entry(check: Check<UByte>) = CredentialStatusEntry.Bitstring("ref", BitstringPurpose.Known.REVOCATION, check)
    private fun status(vararg entries: CredentialStatusEntry) = CredentialStatus.references(entries.toList())

    @Test
    fun homeIsQuietForAbsenceUncheckedAndFreshClearButKeepsKnownAdverseResults() {
        val at = checked + 1.seconds
        val absent = status().presentation(at)
        assertEquals(CredentialStatusIssue.NONE, absent.issue)
        assertEquals(CredentialStatusKind.NOT_PROVIDED, absent.kind)
        assertEquals(CredentialStatusCoverage.NONE, absent.coverage)
        assertEquals(CredentialStatusIssue.NONE, status(entry(Check.NotChecked)).presentation(at).issue)
        assertEquals(CredentialStatusIssue.NONE, status(entry(Check.Observed(observation()))).presentation(at).issue)
        val adverse = status(entry(Check.Failed(checked + 40.seconds, CheckFailure.Reason.TRANSPORT, observation(1u))))
            .presentation(checked + 40.seconds)
        assertEquals(CredentialStatusKind.REVOKED, adverse.kind)
        assertEquals(CredentialStatusIssue.REVOKED, adverse.issue)
        assertEquals(CredentialStatusFreshness.STALE, adverse.freshness)
        assertEquals(checked, adverse.lastCheckedAt)
        assertEquals(CheckFailure.Reason.TRANSPORT, adverse.failure)
        assertEquals(StatusMeaning.REVOKED, adverse.references.single().result)
    }

    @Test
    fun freshNetworkFailureIsFeedbackWhileUnverifiedOrPartialCoverageNeedsAttention() {
        val previous = observation()
        val transient = status(entry(Check.Failed(checked + 1.seconds, CheckFailure.Reason.TRANSPORT, previous)))
            .presentation(checked + 1.seconds)
        assertEquals(CredentialStatusIssue.NONE, transient.issue)
        val unverified = status(entry(Check.Failed(checked + 1.seconds, CheckFailure.Reason.INVALID_SIGNATURE, previous)))
            .presentation(checked + 1.seconds)
        assertEquals(CredentialStatusKind.UNVERIFIED, unverified.kind)
        val partial = status(entry(Check.Observed(previous)), CredentialStatusEntry.Unsupported("other", "identifier_list"))
            .presentation(checked + 1.seconds)
        assertEquals(CredentialStatusKind.PARTIAL, partial.kind)
        assertEquals(CredentialStatusCoverage.PARTIAL, partial.coverage)
        assertEquals(CredentialStatusKind.UNAVAILABLE,
            CredentialStatus.DiscoveryFailed(DiscoveryProblem.MALFORMED_CONTAINER).presentation(checked).kind)
        assertEquals(DiscoveryProblem.RESOURCE_LIMIT,
            CredentialStatus.DiscoveryFailed(DiscoveryProblem.RESOURCE_LIMIT).presentation(checked).discoveryProblem)
    }

    @Test
    fun advisoryRevisionIgnoresCheckTimesButChangesAtFreshnessSeverityAndNewWarnings() {
        val old = status(entry(Check.Observed(observation())))
        val updated = status(entry(Check.Observed(observation(at = checked + 1.seconds))))
        assertEquals(old.presentation(checked + 2.seconds).advisoryRevision, updated.presentation(checked + 2.seconds).advisoryRevision)
        assertNotEquals(old.presentation(checked + 2.seconds).advisoryRevision, old.presentation(checked + 30.seconds).advisoryRevision)
        val revoked = status(entry(Check.Observed(observation(1u))))
        assertNotEquals(old.presentation(checked + 2.seconds).advisoryRevision, revoked.presentation(checked + 2.seconds).advisoryRevision)
        val unsupported = CredentialStatusEntry.Unsupported("other", "unknown")
        val warning = status(entry(Check.Observed(observation(1u))), unsupported).presentation(checked + 1.seconds)
        val failed = status(entry(Check.Failed(checked + 1.seconds, CheckFailure.Reason.INVALID_SIGNATURE, observation(1u))), unsupported)
            .presentation(checked + 1.seconds)
        assertEquals(warning.kind, failed.kind)
        assertEquals(warning.coverage, failed.coverage)
        assertNotEquals(warning.advisoryRevision, failed.advisoryRevision, "A newly unverified check must not be hidden by an existing adverse headline")
        fun token(value: TokenStatus) = CredentialStatusEntry.Token("token",
            Check.Observed(Observation(value, checked, checked + 30.seconds)))
        assertNotEquals(status(entry(Check.Observed(observation(1u))), token(TokenStatus.Known.CLEAR)).presentation(checked + 1.seconds).advisoryRevision,
            status(entry(Check.Observed(observation(1u))), token(TokenStatus.Known.SUSPENDED)).presentation(checked + 1.seconds).advisoryRevision,
            "A new secondary adverse result must not be hidden by the primary revocation")
    }

    @Test
    fun historicalAdverseWordingTracksThePrimaryEvidenceRatherThanAnUnrelatedStaleReference() {
        val revoked = entry(Check.Observed(observation(1u, checked + 20.seconds)))
        val staleSibling = CredentialStatusEntry.Bitstring("suspension", BitstringPurpose.Known.SUSPENSION,
            Check.Observed(observation()))
        val combined = status(revoked, staleSibling)
        val current = combined.presentation(checked + 40.seconds)
        assertEquals(CredentialStatusKind.REVOKED, current.kind)
        assertEquals(CredentialStatusFreshness.STALE, current.freshness)
        assertFalse(current.lastKnown, "Stale sibling evidence must not relabel a current revocation")
        val staleRevocation = CredentialStatusEntry.Bitstring("older-revocation", BitstringPurpose.Known.REVOCATION,
            Check.Observed(observation(1u)))
        val reordered = listOf(status(staleRevocation, revoked), status(revoked, staleRevocation))
            .map { it.presentation(checked + 40.seconds) }
        assertTrue(reordered.none { it.lastKnown }, "Fresh evidence of the same primary result wins over stale evidence")
        assertEquals(reordered.first().advisoryRevision, reordered.last().advisoryRevision)

        val historical = combined.presentation(checked + 50.seconds)
        assertTrue(historical.lastKnown)
        assertEquals(current.freshness, historical.freshness)
        assertEquals(current.coverage, historical.coverage)
        assertNotEquals(current.advisoryRevision, historical.advisoryRevision,
            "The primary warning becoming historical must invalidate its reviewed wording")
    }

    @Test
    fun displayPurposeKeysAndHeadlinesRespectEachProtocolsVocabulary() {
        val legacy = status(CredentialStatusEntry.Legacy("legacy", LegacyPurpose.fromValue("refresh"),
            Check.Observed(Observation(true, checked, checked + 30.seconds)))).presentation(checked + 1.seconds)
        val bitstring = status(CredentialStatusEntry.Bitstring("bitstring", BitstringPurpose.Known.REFRESH,
            Check.Observed(observation(1u)))).presentation(checked + 1.seconds)
        assertEquals(listOf("other"), legacy.purposes)
        assertEquals("other", legacy.references.single().purpose)
        assertEquals(StatusMeaning.APPLICATION_SPECIFIC, legacy.references.single().result)
        assertEquals(listOf("refresh"), bitstring.purposes)
        assertEquals(StatusMeaning.REFRESH, bitstring.references.single().result)
        assertNotEquals(legacy.advisoryRevision, bitstring.advisoryRevision)
        val token = status(CredentialStatusEntry.Token("token",
            Check.Observed(Observation(TokenStatus.Known.CLEAR, checked, checked + 30.seconds)))).presentation(checked + 1.seconds)
        val mso = status(CredentialStatusEntry.Mso("mso",
            Check.Observed(Observation(MsoStatus.CLEAR, checked, checked + 30.seconds)))).presentation(checked + 1.seconds)
        assertEquals(CredentialStatusKind.CHECKED, token.kind, "Token clear is not a revocation-purpose assertion")
        assertEquals(CredentialStatusKind.NOT_REVOKED, mso.kind)
    }
}
