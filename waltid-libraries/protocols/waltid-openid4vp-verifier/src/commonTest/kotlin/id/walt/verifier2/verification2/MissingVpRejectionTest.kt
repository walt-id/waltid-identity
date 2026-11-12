package id.walt.verifier2.verification2

import id.walt.verifier2.data.SessionEvent
import id.walt.verifier2.data.SessionFailure
import id.walt.verifier2.data.Verification2Session
import id.walt.verifier2.data.Verification2Session.VerificationSessionStatus.FAILED
import id.walt.verifier2.handlers.vpresponse.Verifier2VPDirectPostHandler
import id.walt.verifier2.handlers.vpresponse.Verifier2VPDirectPostHandler.CleartextDirectPostResponse
import id.walt.verifier2.handlers.vpresponse.Verifier2VPDirectPostHandler.PresentationRejectionException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class MissingVpRejectionTest {
    @Test
    fun missingVpMarksTheSessionFailedBeforeRejecting() = runTest {
        val query = """{"credentials":[{"id":"identity","format":"jwt_vc_json","meta":{"type_values":[["VerifiableCredential"]]}}]}"""
        val session = Json.decodeFromString<Verification2Session>("""
            {
              "id":"missing-vp",
              "setup":{"flow_type":"cross_device","core_flow":{"dcql_query":$query}},
              "creationDate":"2026-11-01T00:00:00Z","status":"IN_USE",
              "authorizationRequestUrl":"openid4vp://authorize","requestMode":"REQUEST_URI_SIGNED",
              "authorizationRequest":{
                "response_type":"vp_token","client_id":"redirect_uri:https://verifier.example/response",
                "response_mode":"direct_post","response_uri":"https://verifier.example/response",
                "nonce":"nonce","state":"state","dcql_query":$query,
                "client_metadata":{"vp_formats_supported":{"jwt_vc_json":{}}}
              },
              "policies":{"vp_policies":{"jwt_vc_json":[]}}
            }
        """.trimIndent())
        val failures = mutableListOf<SessionEvent>()
        val jwt = "eyJhbGciOiJFUzI1NiJ9.e30." + "A".repeat(86)
        val rejection = assertFailsWith<PresentationRejectionException> {
            Verifier2VPDirectPostHandler.handleDirectPost(
                verificationSession = session,
                responseData = CleartextDirectPostResponse("""{"identity":["$jwt"]}""", "state"),
                updateSessionCallback = { current, _, block -> current.block() },
                failSessionCallback = { current, event, update ->
                    failures += event
                    update(current, event) { status = FAILED }
                },
            )
        }
        val expectedReason = "Presentation for query 'identity' is missing the required 'vp' claim."
        assertEquals(expectedReason, rejection.message)
        assertEquals(FAILED, session.status)
        assertEquals(listOf(SessionEvent.presentation_validation_failed), failures)
        val failure = assertIs<SessionFailure.PresentationValidation>(session.failure)
        assertEquals(expectedReason, failure.reason)
        assertEquals(emptyMap(), failure.failedPolicies)
    }
}
