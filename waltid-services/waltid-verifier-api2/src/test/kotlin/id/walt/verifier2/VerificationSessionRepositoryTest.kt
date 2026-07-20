package id.walt.verifier2

import id.walt.commons.web.plugins.configureStatusPages
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier2.data.CrossDeviceFlowSetup
import id.walt.verifier2.data.GeneralFlowConfig
import id.walt.verifier2.data.Verification2Session
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.http.ContentType
import io.ktor.http.Url
import io.ktor.http.contentType
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

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
    fun `failed parsing can restore an unchanged processing claim`() = runTest {
        val repository = InMemoryVerificationSessionRepository()
        repository.create(session("retryable"))
        val claim = repository.claimForProcessingWithOriginal("retryable")

        repository.restoreProcessingClaim(claim)

        assertEquals(Verification2Session.VerificationSessionStatus.ACTIVE, repository.get("retryable")!!.session.status)
        repository.claimForProcessing("retryable")
    }

    @Test
    fun `claim rollback never overwrites persisted processing progress`() = runTest {
        val repository = InMemoryVerificationSessionRepository()
        repository.create(session("progressed"))
        val claim = repository.claimForProcessingWithOriginal("progressed")
        repository.update("progressed") { attempted = true }

        repository.restoreProcessingClaim(claim)

        assertEquals(true, repository.get("progressed")!!.session.attempted)
    }

    @Test
    fun `terminal session uses retention instead of request expiration`() = runTest {
        val repository = InMemoryVerificationSessionRepository()
        val now = Clock.System.now()
        val completed = session("completed").copy(
            expirationDate = now - 1.minutes,
            retentionDate = now + 1.minutes,
            status = Verification2Session.VerificationSessionStatus.SUCCESSFUL,
            attempted = true,
        )
        val unused = session("unused").copy(
            expirationDate = now - 1.minutes,
            retentionDate = now + 1.minutes,
        )
        val expired = session("expired").copy(
            expirationDate = now - 1.minutes,
            retentionDate = now + 1.minutes,
            status = Verification2Session.VerificationSessionStatus.EXPIRED,
        )
        repository.create(completed)
        repository.create(unused)
        repository.create(expired)

        assertNotNull(repository.get("completed"))
        assertNull(repository.get("unused"))
        assertNull(repository.get("expired"))
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

    @Test
    fun `malformed direct-post response restores session for retry`() = testApplication {
        val repository = InMemoryVerificationSessionRepository()
        repository.create(session("malformed"))
        application {
            install(ContentNegotiation) { json() }
            install(SSE)
            configureStatusPages()
            routing { Verifier2Service.run { registerRoute(repository) } }
        }

        val response = client.post("/verification-session/malformed/response") {
            contentType(ContentType.Application.Json)
            setBody("{")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(Verification2Session.VerificationSessionStatus.ACTIVE, repository.get("malformed")!!.session.status)
        repository.claimForProcessing("malformed")
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
