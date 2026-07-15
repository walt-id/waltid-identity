package id.walt.verifier2

import id.walt.commons.web.plugins.configureStatusPages
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier2.data.CrossDeviceFlowSetup
import id.walt.verifier2.data.GeneralFlowConfig
import id.walt.verifier2.data.Verification2Session
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.testing.testApplication
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VerificationSessionRepositoryTest {
    @Test
    fun `create rejects duplicate session id`() = runTest {
        val repository = InMemoryVerificationSessionRepository()
        repository.create(session("duplicate"))

        assertFailsWith<DuplicateVerificationSessionException> { repository.create(session("duplicate")) }
    }

    @Test
    fun `compare and set rejects stale version`() = runTest {
        val repository = InMemoryVerificationSessionRepository()
        val initial = repository.create(session("stale"))
        repository.compareAndSet("stale", initial.version, initial.session.apply { attempted = true })

        assertFailsWith<StaleVerificationSessionException> {
            repository.compareAndSet("stale", initial.version, initial.session)
        }
    }

    @Test
    fun `concurrent updates are atomic and preserve all mutations`() = runTest {
        val repository = InMemoryVerificationSessionRepository()
        repository.create(session("concurrent"))

        coroutineScope {
            repeat(50) {
                launch { repository.update("concurrent") { statusReason = (statusReason ?: "") + "x" } }
            }
        }

        assertEquals(50, repository.get("concurrent")!!.session.statusReason!!.length)
        assertEquals(50, repository.get("concurrent")!!.version)
    }

    @Test
    fun `returned sessions cannot mutate stored snapshots`() = runTest {
        val repository = InMemoryVerificationSessionRepository()
        val created = repository.create(session("isolated"))
        created.session.status = Verification2Session.VerificationSessionStatus.FAILED

        assertEquals(Verification2Session.VerificationSessionStatus.ACTIVE, repository.get("isolated")!!.session.status)
    }

    @Test
    fun `info route returns 404 for missing session`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            install(SSE)
            configureStatusPages()
            routing { Verifier2Service.run { registerRoute(InMemoryVerificationSessionRepository()) } }
        }

        assertEquals(HttpStatusCode.NotFound, client.get("/verification-session/missing/info").status)
    }

    @Test
    fun `info route returns 503 when repository is unavailable`() = testApplication {
        val unavailable = object : VerificationSessionRepository {
            override suspend fun create(session: Verification2Session) = error("unused")
            override suspend fun get(sessionId: String): VerificationSessionSnapshot? =
                throw VerificationSessionRepositoryUnavailableException("database unavailable")

            override suspend fun compareAndSet(
                sessionId: String,
                expectedVersion: Long,
                session: Verification2Session,
            ) = error("unused")
            override suspend fun delete(sessionId: String) = error("unused")
        }
        application {
            install(ContentNegotiation) { json() }
            install(SSE)
            configureStatusPages()
            routing { Verifier2Service.run { registerRoute(unavailable) } }
        }

        assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/verification-session/missing/info").status)
    }

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
