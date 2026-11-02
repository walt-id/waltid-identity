package id.walt.verifier2

import id.walt.commons.web.plugins.configureStatusPages
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import id.walt.verifier2.data.CrossDeviceFlowSetup
import id.walt.verifier2.data.GeneralFlowConfig
import id.walt.verifier2.data.Verification2Session
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class Verifier2OfferConcurrencyTest {
    @Test
    fun `fetches do not contend with presentations and failed attempts stay retryable`() = testApplication {
        val id = "release-offer-concurrency"
        val session = Verification2Session(
            id = id,
            setup = CrossDeviceFlowSetup(core = GeneralFlowConfig(reattemptable = false)),
            reattemptable = false,
            authorizationRequest = AuthorizationRequest(
                clientId = "redirect_uri:https://verifier.example/response",
                state = "expected-state",
                responseMode = OpenID4VPResponseMode.DIRECT_POST,
                responseUri = "https://verifier.example/response",
            ),
            authorizationRequestUrl = Url("openid4vp://authorize"),
            requestMode = Verification2Session.RequestMode.URL_ENCODED,
            status = Verification2Session.VerificationSessionStatus.ACTIVE,
        )
        Verifier2Service.sessions[id] = session
        application {
            install(ContentNegotiation) { json() }
            install(SSE)
            configureStatusPages()
            routing { Verifier2Service.run { registerRoute() } }
        }
        try {
            Verifier2Service.withProcessingClaim(id) {
                assertEquals(HttpStatusCode.OK, client.get("/verification-session/$id/request").status)
                assertEquals(HttpStatusCode.OK, client.post("/verification-session/$id/request") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody("wallet_nonce=wallet")
                }.status)
                assertEquals(HttpStatusCode.Conflict, client.post("/verification-session/$id/response").status)
                assertEquals(Verification2Session.VerificationSessionStatus.ACTIVE, session.status)
            }
            var previousId: String? = null
            for (error in listOf("access_denied", "invalid_request")) {
                assertEquals(HttpStatusCode.OK, client.post("/verification-session/$id/response") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody("state=expected-state&error=$error")
                }.status)
                val presentationId = assertNotNull(session.presentationId)
                assertNotEquals(previousId, presentationId)
                assertEquals(Verification2Session.VerificationSessionStatus.FAILED, session.status)
                previousId = presentationId
            }
            session.status = Verification2Session.VerificationSessionStatus.SUCCESSFUL
            assertEquals(HttpStatusCode.BadRequest, client.get("/verification-session/$id/request").status)
            assertEquals(HttpStatusCode.BadRequest, client.post("/verification-session/$id/response") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody("state=expected-state&error=access_denied")
            }.status)
            assertEquals(previousId, session.presentationId)
            assertEquals(HttpStatusCode.NotFound, client.post("/verification-session/missing-offer/response").status)
        } finally {
            Verifier2Service.sessions.remove(id)
        }
    }

    @Test
    fun `cancelled processing releases its claim`() = runTest {
        val admitted = CompletableDeferred<Unit>()
        val processor = launch {
            Verifier2Service.withProcessingClaim("cancelled-release-offer") {
                admitted.complete(Unit)
                awaitCancellation()
            }
        }
        admitted.await()
        processor.cancelAndJoin()
        var reacquired = false
        Verifier2Service.withProcessingClaim("cancelled-release-offer") { reacquired = true }
        assertEquals(true, reacquired)
    }
}
