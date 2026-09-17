package id.walt.verifier2

import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier2.data.CrossDeviceFlowSetup
import id.walt.verifier2.data.GeneralFlowConfig
import id.walt.verifier2.data.Verification2Session
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * The in-memory session store must not grow without bound.
 *
 * Nothing ever removed a session: retention gives a used session ten years by default, a finished session is
 * never read again, and the only eviction was the lazy one inside `get` for the single id being looked up. So
 * every completed verification stayed resident. At 15 requests per minute that is ~900 sessions an hour, each
 * holding its policy results and the credentials that were presented, which exhausted the heap of a service
 * doing almost no traffic - reported as an OutOfMemoryError rather than as a leak.
 *
 * Two independent guards are asserted here: expired sessions are swept, and a ceiling applies even to sessions
 * that never expire.
 */
class InMemorySessionStoreBoundsTest {

    @Test
    fun `expired sessions are swept instead of accumulating`() = runTest {
        val repository = InMemoryVerificationSessionRepository()
        val past = Clock.System.now() - 1.minutes

        // Terminal sessions whose retention has passed: nothing will ever look these up again.
        repeat(300) { index ->
            repository.create(
                session("expired-$index").copy(
                    status = Verification2Session.VerificationSessionStatus.SUCCESSFUL,
                    attempted = true,
                    retentionDate = past,
                )
            )
        }

        assertTrue(
            repository.size < 300,
            "expired sessions must be dropped as new ones arrive, still holding ${repository.size}",
        )
    }

    @Test
    fun `the store stays bounded when sessions are retained indefinitely`() = runTest {
        // Null retention means "keep forever", which an in-memory map cannot honour. The ceiling is what stops
        // "keep forever" from meaning "until the process dies".
        val repository = InMemoryVerificationSessionRepository(maxSessions = 50)

        repeat(200) { index ->
            repository.create(
                session("kept-$index").copy(
                    status = Verification2Session.VerificationSessionStatus.SUCCESSFUL,
                    attempted = true,
                    retentionDate = null,
                )
            )
        }

        assertEquals(50, repository.size, "the ceiling must hold even when no session ever expires")
        assertTrue(repository.unexpiredEvictions > 0, "dropping a live session must be reported, not silent")
    }

    @Test
    fun `the ceiling discards the least recently used session and keeps the active one`() = runTest {
        val repository = InMemoryVerificationSessionRepository(maxSessions = 3)
        repeat(3) { index -> repository.create(session("session-$index").copy(retentionDate = null)) }

        // Touching session-0 makes session-1 the least recently used.
        assertNotNull(repository.get("session-0"))
        repository.create(session("session-3").copy(retentionDate = null))

        assertNotNull(repository.get("session-0"), "a session still being used must survive the ceiling")
        assertNull(repository.get("session-1"), "the least recently used session is the one to drop")
    }

    @Test
    fun `sweeping does not disturb sessions that are still valid`() = runTest {
        val repository = InMemoryVerificationSessionRepository()
        repository.create(session("live").copy(expirationDate = Clock.System.now() + 10.minutes))

        repeat(SWEEP_TRIGGER) { index ->
            repository.create(
                session("stale-$index").copy(
                    status = Verification2Session.VerificationSessionStatus.SUCCESSFUL,
                    attempted = true,
                    retentionDate = Clock.System.now() - 1.minutes,
                )
            )
        }

        assertNotNull(repository.get("live"), "a valid session must survive a sweep")
    }

    @Test
    fun `storing a session shares its contents instead of re-parsing them`() = runTest {
        // The store used to serialise each session to JSON and parse it back, which re-materialised every
        // element of a byte-array claim: a 224 KiB portrait measured 16.8 MiB once parsed, against nothing at
        // all when the flyweight is shared. Identity is the deterministic way to assert no round trip happened -
        // a re-parsed session would be structurally equal but a different instance.
        val repository = InMemoryVerificationSessionRepository()
        val original = session("shared")

        val created = repository.create(original)
        val fetched = assertNotNull(repository.get("shared"))

        assertSame(original.setup, created.session.setup, "creating must not re-serialise the session")
        assertSame(original.setup, fetched.session.setup, "reading must not re-serialise the session")
        assertSame(
            original.setup,
            repository.update("shared") { status = Verification2Session.VerificationSessionStatus.SUCCESSFUL }
                .session.setup,
            "updating must not re-serialise the session",
        )
    }

    /** Enough creations to cross the internal sweep interval. */
    private val SWEEP_TRIGGER = 200

    private fun session(id: String) = Verification2Session(
        id = id,
        setup = CrossDeviceFlowSetup(
            core = GeneralFlowConfig(
                dcqlQuery = Json.decodeFromString(
                    """{"credentials":[{"id":"stub","format":"dc+sd-jwt","meta":{"vct_values":["https://example.com/stub"]}}]}"""
                ),
            ),
        ),
        authorizationRequest = AuthorizationRequest(clientId = "https://verifier.example.com"),
        authorizationRequestUrl = Url("openid4vp://authorize?client_id=test"),
        requestMode = Verification2Session.RequestMode.URL_ENCODED,
        status = Verification2Session.VerificationSessionStatus.ACTIVE,
    )
}
