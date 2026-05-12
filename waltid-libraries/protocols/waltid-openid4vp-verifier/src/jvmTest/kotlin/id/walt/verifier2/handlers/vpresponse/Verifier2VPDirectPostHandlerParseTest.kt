package id.walt.verifier2.handlers.vpresponse

import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import id.walt.verifier2.data.CrossDeviceFlowSetup
import id.walt.verifier2.data.SessionEvent
import id.walt.verifier2.data.SessionFailure
import id.walt.verifier2.data.Verification2Session
import id.walt.verifier2.handlers.vpresponse.Verifier2VPDirectPostHandler.CleartextDirectPostResponse
import id.walt.verifier2.handlers.vpresponse.Verifier2VPDirectPostHandler.DcApiJsonDirectPostResponse
import id.walt.verifier2.handlers.vpresponse.Verifier2VPDirectPostHandler.DirectPostResponse
import id.walt.verifier2.handlers.vpresponse.Verifier2VPDirectPostHandler.EncryptedResponseStringDirectPostResponse
import id.walt.verifier2.handlers.vpresponse.Verifier2VPDirectPostHandler.ErrorResponseDirectPost
import id.walt.verifier2.handlers.vpresponse.Verifier2VPDirectPostHandler.handleDirectPost
import id.walt.verifier2.handlers.vpresponse.Verifier2VPDirectPostHandler.parseHttpRequestToDirectPostResponse
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.parseUrlEncodedParameters
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.util.AttributeKey
import kotlinx.serialization.ExperimentalSerializationApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull

@OptIn(ExperimentalSerializationApi::class)
class Verifier2VPDirectPostHandlerParseTest {

    private fun parse(contentType: ContentType, body: String): DirectPostResponse {
        val captured = AttributeKey<DirectPostResponse>("captured-response")
        var parsedOut: DirectPostResponse? = null

        testApplication {
            application {
                routing {
                    post("/test") {
                        parsedOut = call.parseHttpRequestToDirectPostResponse()
                        call.respond("ok")
                    }
                }
            }
            val response = client.post("/test") {
                contentType(contentType)
                setBody(body)
            }
            check(response.status.value == 200) { "setup call failed: ${response.status}" }
        }

        return parsedOut ?: error("parseHttpRequestToDirectPostResponse did not return")
    }

    @Test
    fun urlEncoded_errorOnly_parsesToErrorResponseDirectPost() {
        val body = "error=access_denied&error_description=User+denied&state=abc123"
        val parsed = parse(ContentType.Application.FormUrlEncoded, body)

        val errorResponse = assertIs<ErrorResponseDirectPost>(parsed)
        assertEquals("access_denied", errorResponse.error)
        assertEquals("User denied", errorResponse.errorDescription)
        assertEquals("abc123", errorResponse.state)
    }

    @Test
    fun urlEncoded_errorWithoutState_producesErrorResponseWithNullState() {
        val parsed = parse(ContentType.Application.FormUrlEncoded, "error=access_denied")
        val errorResponse = assertIs<ErrorResponseDirectPost>(parsed)
        assertEquals("access_denied", errorResponse.error)
        assertEquals(null, errorResponse.errorDescription)
        assertEquals(null, errorResponse.state)
    }

    @Test
    fun urlEncoded_vpTokenBody_stillRoutesToCleartextVariant() {
        val parsed = parse(
            ContentType.Application.FormUrlEncoded,
            "vp_token=%7B%22pid%22%3A%5B%22ey...%22%5D%7D&state=abc",
        )
        val cleartext = assertIs<CleartextDirectPostResponse>(parsed)
        assertEquals("abc", cleartext.state)
    }

    @Test
    fun urlEncoded_responseBody_stillRoutesToEncryptedVariant() {
        val parsed = parse(ContentType.Application.FormUrlEncoded, "response=eyAbc")
        assertIs<EncryptedResponseStringDirectPostResponse>(parsed)
    }

    @Test
    fun urlEncoded_emptyBody_stillThrows() {
        assertFails {
            parse(ContentType.Application.FormUrlEncoded, "")
        }
    }

    @Test
    fun json_withErrorField_routesToErrorResponseDirectPost() {
        val body = """{"error":"access_denied","error_description":"User denied","state":"abc"}"""
        val parsed = parse(ContentType.Application.Json, body)
        val errorResponse = assertIs<ErrorResponseDirectPost>(parsed)
        assertEquals("access_denied", errorResponse.error)
        assertEquals("abc", errorResponse.state)
    }

    @Test
    fun json_withoutErrorField_routesToDcApiJson() {
        val body = """{"data":{"vp_token":{"pid":[]}}}"""
        val parsed = parse(ContentType.Application.Json, body)
        assertIs<DcApiJsonDirectPostResponse>(parsed)
    }

    @Test
    fun urlEncoded_parsingRoundTrip_spaceEncoding() {
        // Sanity check that `%20` and `+` both decode to space — the parser must preserve
        // error_description exactly as the wallet sent it.
        val params = "error=server_error&error_description=Internal%20server%20problem".parseUrlEncodedParameters()
        assertEquals("Internal server problem", params["error_description"])
    }

    @Test
    fun walletErrorResponse_missingExpectedState_isRejected() = kotlinx.coroutines.test.runTest {
        val session = directPostSession(expectedState = "expected-state")

        assertFailsWith<Throwable> {
            session.handleWalletError(state = null)
        }
    }

    @Test
    fun walletErrorResponse_matchingState_recordsStructuredFailure() = kotlinx.coroutines.test.runTest {
        val session = directPostSession(expectedState = "expected-state")
        val events = session.handleWalletError(state = "expected-state")

        assertEquals(listOf(SessionEvent.wallet_error_response_received), events)
        assertEquals(Verification2Session.VerificationSessionStatus.FAILED, session.status)
        assertEquals("Wallet returned OID4VP error: access_denied", session.statusReason)

        val failure = assertIs<SessionFailure.WalletErrorResponse>(assertNotNull(session.failure))
        assertEquals("access_denied", failure.error)
        assertEquals("User denied", failure.errorDescription)
        assertEquals("expected-state", failure.state)
    }

    private fun directPostSession(expectedState: String) = Verification2Session(
        setup = CrossDeviceFlowSetup.EXAMPLE_SDJWT_PID,
        status = Verification2Session.VerificationSessionStatus.ACTIVE,
        authorizationRequest = AuthorizationRequest(
            state = expectedState,
            responseMode = OpenID4VPResponseMode.DIRECT_POST,
            responseUri = "https://verifier.example/response",
        ),
        authorizationRequestUrl = null,
        requestMode = Verification2Session.RequestMode.REQUEST_URI,
    )

    private suspend fun Verification2Session.handleWalletError(state: String?): List<SessionEvent> {
        val events = mutableListOf<SessionEvent>()
        handleDirectPost(
            verificationSession = this,
            responseData = ErrorResponseDirectPost(
                error = "access_denied",
                errorDescription = "User denied",
                state = state,
            ),
            updateSessionCallback = { session, event, block ->
                events += event
                session.block()
            },
            failSessionCallback = { session, event, updateSession ->
                session.status = Verification2Session.VerificationSessionStatus.FAILED
                updateSession(session, event) {}
            },
        )
        return events
    }
}
