package id.walt.verifier2

import id.walt.verifier2.data.DEFAULT_RETENTION_YEARS
import id.walt.verifier2.data.GeneralFlowConfig
import id.walt.verifier2.data.CrossDeviceFlowSetup
import id.walt.verifier2.handlers.sessioncreation.VerificationSessionCreator
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * How long a verification session is kept has to be a deployment decision.
 *
 * It used to be a literal in the library - `now + 10 years`, reachable from neither service configuration nor
 * the request - so a deployment with a retention obligation had no way to meet it, and the session document's
 * own documentation offered a null retention that the type made impossible.
 *
 * The resolution order is: what the request asks for, else what the verifier is configured for, else the
 * historical default. Only the last of those is a behaviour anyone already depends on, so it is pinned here
 * too.
 */
class VerificationSessionRetentionTest {

    private fun setup(core: GeneralFlowConfig) = CrossDeviceFlowSetup(core = core)

    private suspend fun retentionOf(core: GeneralFlowConfig, configured: Duration? = null) =
        VerificationSessionCreator.createVerificationSession(
            setup = setup(core),
            clientId = "redirect_uri:https://verifier.example.com/response",
            urlPrefix = "https://verifier.example.com",
            urlHost = "openid4vp://authorize",
            retention = configured,
        ).retentionDate

    @Test
    fun `retention falls back to the historical default when nothing configures it`() = runTest {
        val before = Clock.System.now()

        val retention = assertNotNull(retentionOf(GeneralFlowConfig()))

        // Bracketed rather than compared to a single instant, because the creator reads the clock itself.
        assertTrue(
            retention >= before.plus(DEFAULT_RETENTION_YEARS, DateTimeUnit.YEAR, TimeZone.UTC) &&
                    retention <= Clock.System.now().plus(DEFAULT_RETENTION_YEARS, DateTimeUnit.YEAR, TimeZone.UTC),
            "expected the $DEFAULT_RETENTION_YEARS year default, got $retention",
        )
    }

    @Test
    fun `the verifier's configured retention applies when the request asks for none`() = runTest {
        val before = Clock.System.now()

        val retention = assertNotNull(retentionOf(GeneralFlowConfig(), configured = 30.days))

        assertTrue(
            retention >= before + 30.days && retention <= Clock.System.now() + 30.days,
            "expected the configured 30 days, got $retention",
        )
    }

    @Test
    fun `the request overrides the verifier's configured retention`() = runTest {
        val before = Clock.System.now()

        val retention = assertNotNull(
            retentionOf(GeneralFlowConfig(retentionDuration = 2.hours), configured = 30.days),
        )

        assertTrue(
            retention >= before + 2.hours && retention <= Clock.System.now() + 2.hours,
            "the per-session retention_duration should win over the configured 30 days, got $retention",
        )
    }

    @Test
    fun `an explicit retention date wins over a duration`() = runTest {
        val exact = Clock.System.now() + 5.days

        val retention = retentionOf(
            GeneralFlowConfig(retentionDuration = 2.hours, retentionDate = exact),
            configured = 30.days,
        )

        assertEquals(exact, retention)
    }

    @Test
    fun `an infinite configured retention keeps the session indefinitely`() = runTest {
        // Expressed as no retention date at all, which is what persistenceExpirationDate() turns into "never
        // discard" - and what the TTL index leaves alone.
        val retention = retentionOf(GeneralFlowConfig(), configured = Duration.INFINITE)

        assertNull(retention, "an infinite retention must not become a date")
    }
}
