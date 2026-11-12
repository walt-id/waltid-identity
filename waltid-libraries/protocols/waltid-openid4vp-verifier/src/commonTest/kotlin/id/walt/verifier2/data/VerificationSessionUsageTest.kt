package id.walt.verifier2.data

import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class VerificationSessionUsageTest {
    private fun session(usage: VerificationSessionUsage? = null, reattemptable: Boolean = true) = Verification2Session(
        setup = CrossDeviceFlowSetup(core = GeneralFlowConfig(sessionUsage = usage)),
        status = Verification2Session.VerificationSessionStatus.UNUSED,
        reattemptable = reattemptable,
        authorizationRequest = AuthorizationRequest(responseType = null),
        authorizationRequestUrl = null,
        requestMode = Verification2Session.RequestMode.REQUEST_URI,
    )

    @Test
    fun legacyConfigurationRetainsItsBehavior() {
        for (retry in listOf(false, true)) {
            val session = session(reattemptable = retry)
            session.status = Verification2Session.VerificationSessionStatus.FAILED
            session.attempted = true
            session.requireAvailable()
            session.status = Verification2Session.VerificationSessionStatus.SUCCESSFUL
            assertEquals(!retry, session.isConsumed)
        }
    }

    @Test
    fun explicitModesOverrideTheLegacyFlagAndEnforceAllOutcomes() {
        for (mode in VerificationSessionUsage.entries) {
            for (retry in listOf(false, true)) {
                val session = session(mode, retry)
                session.requireAvailable()
                assertFalse(session.isConsumed)
                session.attempted = true
                session.status = Verification2Session.VerificationSessionStatus.FAILED
                assertEquals(mode == VerificationSessionUsage.SINGLE_ATTEMPT, session.isConsumed)
                session.status = Verification2Session.VerificationSessionStatus.SUCCESSFUL
                assertEquals(mode != VerificationSessionUsage.REUSABLE, session.isConsumed)
            }
        }
    }

    @Test
    fun strictModeIsConsumedBeforeValidationFinishesAndCannotBeReopenedByStatusChanges() {
        val session = session(VerificationSessionUsage.SINGLE_ATTEMPT)
        session.attempted = true
        for (status in Verification2Session.VerificationSessionStatus.entries) {
            session.status = status
            assertTrue(session.isConsumed)
            assertFailsWith<IllegalArgumentException> { session.requireAvailable() }
        }
    }

    @Test
    fun expiryAppliesToEveryModeAndStoredSessionsKeepTheirPolicy() {
        for (mode in VerificationSessionUsage.entries) {
            val session = session(mode).copy(expirationDate = Clock.System.now() - 1.minutes)
            assertFailsWith<IllegalArgumentException> { session.requireAvailable() }
            session.status = Verification2Session.VerificationSessionStatus.FAILED
            session.attempted = true
            val restored = Json.decodeFromString<Verification2Session>(Json.encodeToString(session))
            assertEquals(mode, restored.sessionUsage)
            assertEquals(session.isConsumed, restored.isConsumed)
        }
    }

    @Test
    fun wireNamesAreStableAndUnknownModesAreRejected() {
        val names = listOf("single_attempt", "single_success", "reusable")
        for ((mode, name) in VerificationSessionUsage.entries.zip(names)) {
            assertEquals(mode, Json.decodeFromString<GeneralFlowConfig>("""{"session_usage":"$name"}""").sessionUsage)
        }
        assertFailsWith<SerializationException> { Json.decodeFromString<GeneralFlowConfig>("""{"session_usage":"unknown"}""") }
    }
}
