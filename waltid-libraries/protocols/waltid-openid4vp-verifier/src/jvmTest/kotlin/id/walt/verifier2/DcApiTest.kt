@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.verifier2

import id.walt.crypto2.jose.CompactJws
import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.parser.MdocParser
import id.walt.mdoc.verification.verifyDeviceAuthentication
import id.walt.policies2.vc.VCPolicyList
import id.walt.policies2.vc.policies.CredentialSignaturePolicy
import id.walt.policies2.vp.policies.VPPolicyList
import id.walt.policies2.vp.policies.VPVerificationPolicyManager
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import id.walt.verifier2.data.DcApiAnnexDFlowSetup
import id.walt.verifier2.data.GeneralFlowConfig
import id.walt.verifier2.data.Verification2Session
import id.walt.verifier2.data.Verification2Session.RequestMode
import id.walt.verifier2.handlers.vpresponse.Verifier2VPDirectPostHandler
import id.walt.verifier2.handlers.vpresponse.Verifier2VPDirectPostHandler.DcApiJsonDirectPostResponse
import id.waltid.openid4vp.wallet.presentation.MdocPresenter
import io.ktor.http.URLBuilder
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlin.test.Ignore
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.time.Instant

class DcApiTest {

    private companion object {
        private const val ORIGIN = "https://portal2.demo.walt.id"
        private const val NONCE = "b9cc2837-fbe2-4c2c-a047-750071aa0063"
        private const val CLIENT_ID = "x509_hash:abc-xyz-base64url-sha256-hash-of-der-x509-leaf"
        private const val DOCTYPE = "org.iso.18013.5.1.mDL"

        private val dcqlQuery = Json.decodeFromString<id.walt.dcql.models.DcqlQuery>(
            """
            {
              "credentials": [
                {
                  "id": "my_mdl",
                  "format": "mso_mdoc",
                  "meta": {"doctype_value": "$DOCTYPE"},
                  "claims": [
                    {"path": ["org.iso.18013.5.1", "family_name"]},
                    {"path": ["org.iso.18013.5.1", "given_name"]}
                  ]
                }
              ]
            }
            """.trimIndent(),
        )

        private fun clientMetadata(issuerAlgorithm: Int = -9) = ClientMetadata(
            vpFormatsSupported = mapOf(
                "mso_mdoc" to buildJsonObject {
                    put("issuerauth_alg_values", JsonArray(listOf(JsonPrimitive(issuerAlgorithm))))
                    put("deviceauth_alg_values", JsonArray(listOf(JsonPrimitive(-9))))
                },
            ),
        )

        private val authorizationRequest = AuthorizationRequest(
            clientId = CLIENT_ID,
            state = null,
            responseMode = OpenID4VPResponseMode.DC_API,
            nonce = NONCE,
            dcqlQuery = dcqlQuery,
            clientMetadata = clientMetadata(),
            expectedOrigins = listOf(ORIGIN),
        )

        private val policies = Verification2Session.DefinedVerificationPolicies(
            vp_policies = VPPolicyList(
                jwtVcJson = emptyList(),
                dcSdJwt = emptyList(),
                msoMdoc = VPVerificationPolicyManager.defaultMsoMdocPolicies,
            ),
            vc_policies = VCPolicyList(listOf(CredentialSignaturePolicy())),
        )

        private val staticSession = Verification2Session(
            id = "610965bb-3b17-4e70-90c7-45bd4c26b282",
            setup = DcApiAnnexDFlowSetup(
                core = GeneralFlowConfig(
                    dcqlQuery = dcqlQuery,
                    signedRequest = true,
                    encryptedResponse = false,
                    policies = policies,
                    clientMetadata = clientMetadata(),
                    clientId = CLIENT_ID,
                ),
                expectedOrigins = listOf(ORIGIN),
            ),
            creationDate = Instant.parse("2026-01-03T00:00:00Z"),
            expirationDate = Instant.parse("2026-01-03T00:05:00Z"),
            retentionDate = Instant.parse("2036-01-03T00:00:00Z"),
            status = Verification2Session.VerificationSessionStatus.UNUSED,
            authorizationRequest = authorizationRequest,
            authorizationRequestUrl = authorizationRequest.toHttpUrl(URLBuilder(ORIGIN)),
            signedAuthorizationRequestJwt = SIGNED_AUTHORIZATION_REQUEST_JWT,
            requestMode = RequestMode.REQUEST_URI_SIGNED,
            policies = policies,
        )

        private const val SIGNED_AUTHORIZATION_REQUEST_JWT =
            "eyJhbGciOiJFUzI1NiIsInR5cCI6Im9hdXRoLWF1dGh6LXJlcStqd3QiLCJ4NWMiOlsiTUlJQ0NUQ0NBYkNnQXdJQkFnSVVjRU5RQStDT3FPYkJKMzB5Tk56UVFHelkyc2N3Q2dZSUtvWkl6ajBFQXdJd0tERUxNQWtHQTFVRUJoTUNRVlF4R1RBWEJnTlZCQU1NRUZkaGJIUnBaQ0JVWlhOMElFbEJRMEV3SGhjTk1qWXdNVEF4TURBd01EQXdXaGNOTkRZd01UQXhNREF3TURBd1dqQXpNUXN3Q1FZRFZRUUdFd0pCVkRFa01DSUdBMVVFQXd3YlYyRnNkR2xrSUZSbGMzUWdSRzlqZFcxbGJuUWdVMmxuYm1WeU1Ga3dFd1lIS29aSXpqMENBUVlJS29aSXpqMERBUWNEUWdBRVhXY1B0azF3cnRrRU90bkpIZXVGcEZUelgzempjTE9iMnBpTUl6TnNVaFFtbVBWSm9HNTZFS25xZTFKME1PbFQ3WUhxMzNLeUF0ekZqcm5VOXpyQURLT0JyRENCcVRBZkJnTlZIU01FR0RBV2dCVFhlTjhpUzNSM1hPZVlKQWZRNG9WUnVlQ1Q4REFkQmdOVkhRNEVGZ1FVSkZIUytTQ2dvNUI2UFJxVXBKMDRzSXVrR3pvd0RnWURWUjBQQVFIL0JBUURBZ0NBTUJvR0ExVWRFZ1FUTUJHR0QyaDBkSEJ6T2k4dmQyRnNkQzVwWkRBVkJnTlZIU1VCQWY4RUN6QUpCZ2NvZ1l4ZEJRRUNNQ1FHQTFVZEh3UWRNQnN3R2FBWG9CV0dFMmgwZEhCek9pOHZkMkZzZEM1cFpDOWpjbXd3Q2dZSUtvWkl6ajBFQXdJRFJ3QXdSQUlnRmttU2pXc1VMR0hJZmk4dU1MSVJtL2pYR0VLM2JsVU40S2E3c25tOGlEY0NJRVlydE1qRmR4YUo3NlFyM3NMWS9kRlM0b2tta3JibHBkVVo1aHRLWjFFcyJdfQ.eyJyZXNwb25zZV90eXBlIjoidnBfdG9rZW4iLCJjbGllbnRfaWQiOiJ4NTA5X2hhc2g6YWJjLXh5ei1iYXNlNjR1cmwtc2hhMjU2LWhhc2gtb2YtZGVyLXg1MDktbGVhZiIsInJlc3BvbnNlX21vZGUiOiJkY19hcGkiLCJub25jZSI6ImI5Y2MyODM3LWZiZTItNGMyYy1hMDQ3LTc1MDA3MWFhMDA2MyIsImRjcWxfcXVlcnkiOnsiY3JlZGVudGlhbHMiOlt7ImlkIjoibXlfbWRsIiwiZm9ybWF0IjoibXNvX21kb2MiLCJtZXRhIjp7ImRvY3R5cGVfdmFsdWUiOiJvcmcuaXNvLjE4MDEzLjUuMS5tREwifSwiY2xhaW1zIjpbeyJwYXRoIjpbIm9yZy5pc28uMTgwMTMuNS4xIiwiZmFtaWx5X25hbWUiXX0seyJwYXRoIjpbIm9yZy5pc28uMTgwMTMuNS4xIiwiZ2l2ZW5fbmFtZSJdfV19XX0sImNsaWVudF9tZXRhZGF0YSI6eyJ2cF9mb3JtYXRzX3N1cHBvcnRlZCI6eyJtc29fbWRvYyI6eyJpc3N1ZXJhdXRoX2FsZ192YWx1ZXMiOlstOV0sImRldmljZWF1dGhfYWxnX3ZhbHVlcyI6Wy05XX19fSwiZXhwZWN0ZWRfb3JpZ2lucyI6WyJodHRwczovL3BvcnRhbDIuZGVtby53YWx0LmlkIl0sImF1ZCI6Imh0dHBzOi8vc2VsZi1pc3N1ZWQubWUvdjIiLCJpYXQiOjE3NjczOTg0MDAsImV4cCI6MTc2NzM5ODcwMH0.yEVcfUw6mXSouFsVhr-XRKENZTQoUbRVoLsXfxXprUSOMBF5ALT3YVSnAXpMcbEoORbT2u63QphzseQdqYdjgQ"

        // Static DeviceResponse generated from MdlTestFixture and HOLDER_JWK for ORIGIN/NONCE.
        private const val VP_TOKEN =
            "o2d2ZXJzaW9uYzEuMGlkb2N1bWVudHOBo2dkb2NUeXBldW9yZy5pc28uMTgwMTMuNS4xLm1ETGxpc3N1ZXJTaWduZWSiam5hbWVTcGFjZXOhcW9yZy5pc28uMTgwMTMuNS4xgtgYWFKkaGRpZ2VzdElEAGZyYW5kb21QmTHXTFb_E6gbHENTyvaGZ3FlbGVtZW50SWRlbnRpZmllcmtmYW1pbHlfbmFtZWxlbGVtZW50VmFsdWVjRG9l2BhYUqRoZGlnZXN0SUQBZnJhbmRvbVAj0EtnGa0augmUzWxN5zoGcWVsZW1lbnRJZGVudGlmaWVyamdpdmVuX25hbWVsZWxlbWVudFZhbHVlZEpvaG5qaXNzdWVyQXV0aIRDoQEmoRghWQINMIICCTCCAbCgAwIBAgIUcENQA-COqObBJ30yNNzQQGzY2scwCgYIKoZIzj0EAwIwKDELMAkGA1UEBhMCQVQxGTAXBgNVBAMMEFdhbHRpZCBUZXN0IElBQ0EwHhcNMjYwMTAxMDAwMDAwWhcNNDYwMTAxMDAwMDAwWjAzMQswCQYDVQQGEwJBVDEkMCIGA1UEAwwbV2FsdGlkIFRlc3QgRG9jdW1lbnQgU2lnbmVyMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEXWcPtk1wrtkEOtnJHeuFpFTzX3zjcLOb2piMIzNsUhQmmPVJoG56EKnqe1J0MOlT7YHq33KyAtzFjrnU9zrADKOBrDCBqTAfBgNVHSMEGDAWgBTXeN8iS3R3XOeYJAfQ4oVRueCT8DAdBgNVHQ4EFgQUJFHS-SCgo5B6PRqUpJ04sIukGzowDgYDVR0PAQH_BAQDAgCAMBoGA1UdEgQTMBGGD2h0dHBzOi8vd2FsdC5pZDAVBgNVHSUBAf8ECzAJBgcogYxdBQECMCQGA1UdHwQdMBswGaAXoBWGE2h0dHBzOi8vd2FsdC5pZC9jcmwwCgYIKoZIzj0EAwIDRwAwRAIgFkmSjWsULGHIfi8uMLIRm_jXGEK3blUN4Ka7snm8iDcCIEYrtMjFdxaJ76Qr3sLY_dFS4okmkrblpdUZ5htKZ1EsWQJ02BhZAm-mZ3ZlcnNpb25jMS4wb2RpZ2VzdEFsZ29yaXRobWdTSEEtMjU2bHZhbHVlRGlnZXN0c6Fxb3JnLmlzby4xODAxMy41LjGpAFggeN5dRc2ogp5-sf4xNRdatCrr7X5XpROc7GvoTVjwA4EBWCAb-ijuel8dHtzl1EBTKzb8Z3uGY1P2OZ-6diU_G63i1gJYIGYMNocqLby5nnSCw6czDQXHfBCUaQ6Oj3f4FCfJf4lfA1gg4-R1nobcc2Jk9ov10g71eb8pIoWaBzGfM6j0Ngyg6KIEWCDfkw2UFGv33wiyfhhlWRoj4dBE3mFRnW2dxOduFe-4TQVYILqH4FqG_DvLgc7wHJAvfDSsqnEI59qiXjLFaCQFPzZBBlggdQGLBMYOqWn6XWnUaUOXLRP8dhvE2D0gQVbeJOTZ1ocHWCAkMd2x87pr3ZMbnzH_Rfw4XPq2MX3oJrUkAyJT4K6bRghYIBrgqg_ZrIBGbeheP5dyUAVwHALCNx4GmCXKobiNGPujbWRldmljZUtleUluZm-haWRldmljZUtleaQBAiABIVggeTT2WdzlmOWBItdgSmsqB1_BP69wfuwOe1IYvaY1WdIiWCDBs67cY_TYmI5UhFD-59YtE06YMHqx5gBpsKawYF-v_mdkb2NUeXBldW9yZy5pc28uMTgwMTMuNS4xLm1ETGx2YWxpZGl0eUluZm-jZnNpZ25lZMB0MjAyNi0wMS0wMlQwMDowMDowMFppdmFsaWRGcm9twHQyMDI2LTAxLTAyVDAwOjAwOjAwWmp2YWxpZFVudGlswHQyMDQ1LTEyLTMxVDAwOjAwOjAwWlhAtd6d64iIEbIdf9WKzX9Hvh-sK4HmuTFBvrBvUwMF3AylO9rbBzfndmymv8weDHIb_pOWqh2bGXvdtdSUN_mu32xkZXZpY2VTaWduZWSiam5hbWVTcGFjZXPYGEGgamRldmljZUF1dGihb2RldmljZVNpZ25hdHVyZYRDoQEmoPZYQL_5Q9av2Ca1LVLLB3O9-4m2Wq7PHP1ClH7Qc7yHqlg03VWzesCMxqQXae4vs1tGQ_fKNx355eMag73roNCSRl5mc3RhdHVzAA"

        private val response1 = Json.decodeFromString<JsonObject>(
            """
            {
              "protocol": "openid4vp-v1-signed",
              "data": {
                "vp_token": {
                  "my_mdl": ["$VP_TOKEN"]
                }
              }
            }
            """.trimIndent(),
        )

        private fun session() = staticSession.copy()

        private fun presentedDocument(): Document = MdocParser.parseToDocument(VP_TOKEN)

        private fun signedRequestPayload(): JsonObject = Json.parseToJsonElement(
            CompactJws.decodeUnverified(SIGNED_AUTHORIZATION_REQUEST_JWT).payload.decodeToString(),
        ).jsonObject
    }

    @Test
    fun `static DC API request material stays coherent`() {
        val session = session()
        val setup = assertIs<DcApiAnnexDFlowSetup>(session.setup)
        val url = assertNotNull(session.authorizationRequestUrl)
        val jwtPayload = signedRequestPayload()

        assertEquals(listOf(ORIGIN), setup.expectedOrigins)
        assertEquals(listOf(ORIGIN), session.authorizationRequest.expectedOrigins)
        assertEquals(ORIGIN, url.toString().substringBefore('?'))
        assertEquals(listOf(ORIGIN), Json.decodeFromString(url.parameters["expected_origins"]!!))
        assertEquals(null, url.parameters["state"])
        assertEquals(NONCE, url.parameters["nonce"])
        assertEquals(listOf(ORIGIN), jwtPayload["expected_origins"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(null, jwtPayload["state"])
        assertEquals(NONCE, jwtPayload["nonce"]?.jsonPrimitive?.content)
        assertEquals(
            Json.encodeToJsonElement(
                id.walt.dcql.models.DcqlQuery.serializer(),
                session.authorizationRequest.dcqlQuery!!,
            ),
            jwtPayload["dcql_query"],
        )
    }

    @Test
    fun `static mdoc device auth is signed for the declared DC API origin`() = runTest {
        val document = presentedDocument()
        val transcript = MdocPresenter.buildDcApiSessionTranscript(
            origin = ORIGIN,
            nonce = NONCE,
            encryptionKeyThumbprint = session().jwkThumbprint,
        )

        verifyDeviceAuthentication(
            document = document,
            mso = document.issuerSigned.decodeMobileSecurityObject(),
            sessionTranscript = transcript,
        )
    }

    @Ignore("Temporarily disabled")
    @Test
    fun testDcApi1() = runTest {
        Verifier2VPDirectPostHandler.handleDirectPost(
            verificationSession = session(),
            responseData = DcApiJsonDirectPostResponse(response1),
            updateSessionCallback = { session, event, _ ->
                println(">> Called callback for update session due to $event: $session")
            },
            failSessionCallback = { session, event, _ ->
                println(">> Called callback for fail session due to $event: $session")
            },
            verificationTime = Instant.parse("2026-01-03T00:01:00Z"),
        )
    }

    @Test
    fun `rejects mdoc algorithm outside verifier metadata`() = runTest {
        val verificationSession = session().copy(
            authorizationRequest = authorizationRequest.copy(
                clientMetadata = clientMetadata(issuerAlgorithm = -35),
            ),
        )

        assertFailsWith<Verifier2VPDirectPostHandler.PresentationRejectionException> {
            Verifier2VPDirectPostHandler.handleDirectPost(
                verificationSession = verificationSession,
                responseData = DcApiJsonDirectPostResponse(response1),
                updateSessionCallback = { _, _, _ -> },
                failSessionCallback = { _, _, _ -> },
                verificationTime = Instant.parse("2026-01-03T00:01:00Z"),
            )
        }
    }
}
