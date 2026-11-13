package id.walt.credentials.status

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class CredentialStatusModelTest {
    private val at = Instant.fromEpochSeconds(1_800_000_000)
    private val json = Json { ignoreUnknownKeys = true }
    private fun observation(value: UByte, time: Instant = at) = Observation(value, time, time + 30.seconds)
    private fun revocation(check: Check<UByte>, id: String = "ref") = CredentialStatusEntry.Bitstring(id, BitstringPurpose.Known.REVOCATION, check)
    private fun suspension(check: Check<UByte>) = CredentialStatusEntry.Bitstring("ref", BitstringPurpose.Known.SUSPENSION, check)

    @Test
    fun localReconciliationKeepsTheNewestObservationAndAttemptInEitherUpdateOrder() {
        val clear = observation(0u)
        val revoked = observation(1u)
        val newerRevoked = observation(1u, at + 10.seconds)
        val laterClear = observation(0u, at + 10.seconds)
        val attemptedAt = at + 20.seconds
        val failed = suspension(Check.Failed(attemptedAt, CheckFailure.Reason.TRANSPORT, clear))
        val cases = listOf(
            Triple(suspension(Check.Observed(clear)), suspension(Check.Observed(newerRevoked)), suspension(Check.Observed(newerRevoked))),
            Triple(failed, suspension(Check.Observed(newerRevoked)), suspension(Check.Failed(attemptedAt, CheckFailure.Reason.TRANSPORT, newerRevoked))),
            Triple(revocation(Check.Conflict(attemptedAt, revoked)), revocation(Check.Observed(newerRevoked)), revocation(Check.Conflict(attemptedAt, newerRevoked))),
            Triple(revocation(Check.Observed(revoked)), revocation(Check.Observed(laterClear)), revocation(Check.Conflict(attemptedAt, revoked))),
            Triple(suspension(Check.NotChecked), suspension(Check.Observed(clear)), suspension(Check.Observed(clear))),
        )
        for ((first, second, expected) in cases) {
            assertEquals(expected, first.mergePrevious(second, attemptedAt))
            assertEquals(expected, second.mergePrevious(first, attemptedAt))
        }
    }

    @Test
    fun tokenRegistrationsAndLegacyPurposeDoNotInheritOtherProfiles() {
        assertEquals(TokenStatus.Known.CLEAR, TokenStatus.fromCode(0u))
        assertEquals(TokenStatus.Known.INVALID, TokenStatus.fromCode(1u))
        assertEquals(TokenStatus.Known.SUSPENDED, TokenStatus.fromCode(2u))
        for (code in listOf<UByte>(3u, 12u, 15u)) assertEquals(code, assertIs<TokenStatus.ApplicationSpecific>(TokenStatus.fromCode(code)).code)
        for (code in listOf<UByte>(4u, 11u, 16u, 255u)) assertEquals(code, assertIs<TokenStatus.Reserved>(TokenStatus.fromCode(code)).code)
        assertFailsWith<IllegalArgumentException> { TokenStatus.ApplicationSpecific.create(4u) }
        assertFailsWith<IllegalArgumentException> { TokenStatus.Reserved.create(3u) }
        assertFailsWith<IllegalArgumentException> { BitstringPurpose.Extension.create("revocation") }
        assertFailsWith<IllegalArgumentException> { LegacyPurpose.fromValue(" ") }
        val bitstring = CredentialStatusEntry.Bitstring("ref", BitstringPurpose.fromValue("refresh"), Check.Observed(observation(1u)))
        val legacy = CredentialStatusEntry.Legacy("ref", LegacyPurpose.fromValue("refresh"), Check.Observed(Observation(true, at, at + 30.seconds)))
        assertEquals(StatusMeaning.REFRESH, bitstring.meaning)
        assertEquals(StatusMeaning.APPLICATION_SPECIFIC, legacy.meaning)
        assertFalse(legacy.irreversible)
        assertEquals(CredentialStatusKind.NEEDS_REVIEW, CredentialStatus.references(listOf(legacy)).presentation(at).kind)
    }

    @Test
    fun observationDatesAllowImmediateStaleEvidenceButCannotExtendValidity() {
        val value = observation(0u)
        assertFalse(value.isFresh(at - 1.seconds))
        assertTrue(value.isFresh(at))
        assertFalse(value.isFresh(at + 30.seconds))
        val immediate = Observation(0u.toUByte(), at, at)
        assertFalse(immediate.isFresh(at))
        assertFailsWith<IllegalArgumentException> { Observation(0u.toUByte(), at, at + 30.seconds, at + 20.seconds) }
        val historical = revocation(Check.Observed(value)).historical(at - 10.seconds)
        assertEquals(at, historical.lastObservation()?.checkedAt)
        assertEquals(at - 10.seconds, historical.lastObservation()?.freshUntil)
    }

    @Test
    fun knownWireVariantsRejectForbiddenFieldsWrongValueTypesAndIncompletePayloads() {
        val invalid = listOf(
            """{"type":"mso","referenceId":"ref","purpose":"suspension","check":{"type":"not_checked"}}""",
            """{"type":"token","referenceId":"ref","protocol":"ISO_MSO_STATUS_LIST","check":{"type":"not_checked"}}""",
            """{"type":"unsupported","referenceId":"ref","method":" "}""",
            """{"type":"token","referenceId":"","check":{"type":"not_checked"}}""",
            """{"type":"bitstring","referenceId":"ref","purpose":"","check":{"type":"not_checked"}}""",
            """{"type":"token","referenceId":"ref","check":{"type":"conflict","attemptedAt":"2027-01-15T08:00:00Z"}}""",
            """{"type":"token","referenceId":"ref","check":{"type":"failed","attemptedAt":"2027-01-15T08:00:00Z","cause":{"type":"not_yet_valid"}}}""",
        )
        for (payload in invalid) assertFails(payload) { json.decodeFromString(CredentialStatusEntry.serializer(), payload) }
        val template = """{"type":"TYPE","referenceId":"ref","check":{"type":"observed","observation":{"value":VALUE,"checkedAt":"2027-01-15T08:00:00Z","freshUntil":"2027-01-15T08:00:00Z"}}}"""
        for ((type, value) in listOf("mso" to "1", "legacy" to "1", "token" to "null", "token" to "256", "token" to "\"2\"")) {
            val payload = template.replace("TYPE", type).replace("VALUE", value)
            val withPurpose = if (type == "legacy") payload.replace("\"referenceId\"", "\"purpose\":\"revocation\",\"referenceId\"") else payload
            assertFails(payload) { json.decodeFromString(CredentialStatusEntry.serializer(), withPurpose) }
        }
        val duplicateTag = template.replace("TYPE", "token").replace("VALUE", "0").replace("\"value\"", "\"purpose\":\"suspension\",\"value\"")
        assertFailsWith<SerializationException> { json.decodeFromString(CredentialStatusEntry.serializer(), duplicateTag) }
    }

    @Test
    fun eachProtocolAndCheckRoundTripsWithStableWireTagsAndTypedValues() {
        val values = listOf(
            CredentialStatusEntry.Token("token", Check.Observed(Observation<TokenStatus>(TokenStatus.fromCode(4u), at, at))),
            CredentialStatusEntry.Mso("mso", Check.Observed(Observation(MsoStatus.REVOKED, at, at))),
            revocation(Check.Failed(at + 1.seconds, CheckFailure.RateLimited(at + 5.seconds), observation(1u))),
            CredentialStatusEntry.Legacy("legacy", LegacyPurpose.Known.SUSPENSION, Check.NotChecked),
            CredentialStatusEntry.Mso("conflict", Check.Conflict(at + 1.seconds, Observation(MsoStatus.REVOKED, at, at))),
            CredentialStatusEntry.Unsupported("unsupported", "method"),
            CredentialStatusEntry.Malformed("malformed", ReferenceProblem.MALFORMED),
        )
        val status = CredentialStatus.references(values)
        val encoded = json.encodeToString(CredentialStatus.serializer(), status)
        assertTrue(encoded.contains("\"type\":\"mso\""))
        assertFalse(encoded.contains("id.walt"))
        assertEquals(status, json.decodeFromString(CredentialStatus.serializer(), encoded))
        assertEquals(status, Json { classDiscriminator = "other" }.decodeFromString(CredentialStatus.serializer(),
            Json { classDiscriminator = "other" }.encodeToString(CredentialStatus.serializer(), status)))
        val discovery: CredentialStatus = CredentialStatus.DiscoveryFailed(DiscoveryProblem.RESOURCE_LIMIT)
        assertEquals(discovery, json.decodeFromString(CredentialStatus.serializer(), json.encodeToString(CredentialStatus.serializer(), discovery)))
    }

    @Test
    fun validatedSnapshotsCannotBeChangedThroughInputOrExposedLists() {
        val source = mutableListOf<CredentialStatusEntry>(revocation(Check.NotChecked))
        val status = CredentialStatus.references(source)
        source.clear()
        assertEquals(1, status.entries.size)
        runCatching { (status.entries as MutableList<CredentialStatusEntry>).clear() }
        assertEquals(1, status.entries.size)
        assertFailsWith<IllegalArgumentException> { CredentialStatus.references(listOf(status.entries.single(), status.entries.single())) }
        val display = CredentialStatus.references(listOf(revocation(Check.Observed(observation(0u))))).presentation(at)
        runCatching { (display.purposes as MutableList<String>).clear() }
        assertEquals(listOf("revocation"), display.purposes)
    }

    @Test
    fun mergePreservesBindingAndIrreversibleEvidenceWhileReversibleStatusesRecover() {
        val revoked = revocation(Check.Observed(observation(1u)))
        val conflict = revocation(Check.Observed(observation(0u, at + 5.seconds))).mergePrevious(revoked, at + 5.seconds)
        assertIs<Check.Conflict<*>>(assertIs<CredentialStatusEntry.Bitstring>(conflict).check)
        assertEquals(at, conflict.lastObservation()?.checkedAt)
        val failed = revocation(Check.Failed(at + 6.seconds, CheckFailure.Reason.TRANSPORT)).mergePrevious(conflict, at + 6.seconds)
        assertEquals(StatusMeaning.REVOKED, failed.meaning)
        assertEquals(at, failed.lastObservation()?.checkedAt)
        val confirmed = revocation(Check.Observed(observation(1u, at + 7.seconds))).mergePrevious(failed, at + 7.seconds)
        assertFalse(confirmed.hasConflict)
        assertEquals(at + 7.seconds, confirmed.lastObservation()?.checkedAt)
        val recovered = suspension(Check.Observed(observation(0u, at + 5.seconds)))
            .mergePrevious(suspension(Check.Observed(observation(1u))), at + 5.seconds)
        assertEquals(StatusMeaning.CLEAR, recovered.meaning)
        assertFailsWith<IllegalArgumentException> { revocation(Check.NotChecked, "other").mergePrevious(revoked, at) }
        assertFailsWith<IllegalArgumentException> { suspension(Check.NotChecked).mergePrevious(revoked, at) }
        assertFailsWith<IllegalArgumentException> { CredentialStatusEntry.Token("ref", Check.NotChecked).mergePrevious(revoked, at) }
        assertFailsWith<IllegalArgumentException> {
            CredentialStatusEntry.Token("ref", Check.Conflict(at, Observation<TokenStatus>(TokenStatus.Known.INVALID, at, at)))
        }
        assertFailsWith<IllegalArgumentException> { suspension(Check.Conflict(at, observation(1u))) }
    }

    @Test
    fun temporalFailuresShareOneEligibilityRuleAndAreNotPermanentExpiry() {
        val future = at + 20.seconds
        val waiting = revocation(Check.Failed(at, CheckFailure.NotYetValid(future)))
        assertFalse(waiting.isCheckable(at))
        assertEquals(future, waiting.eligibleAt)
        assertTrue(waiting.isCheckable(future))
        val limited = revocation(Check.Failed(at, CheckFailure.RateLimited(future)))
        assertFalse(limited.isCheckable(at))
        assertTrue(limited.isCheckable(future))
        assertTrue(revocation(Check.Failed(at, CheckFailure.RateLimited())).isCheckable(at))
        assertFalse(revocation(Check.Failed(at, CheckFailure.Reason.CREDENTIAL_EXPIRED)).isCheckable(future))
        assertNotEquals(waiting.failure, CheckFailure.Reason.CREDENTIAL_EXPIRED)
    }
}
