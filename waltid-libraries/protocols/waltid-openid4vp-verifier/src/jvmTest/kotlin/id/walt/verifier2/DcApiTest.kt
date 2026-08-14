@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.verifier2

import id.walt.crypto2.jose.CompactJws
import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.parser.MdocParser
import id.walt.mdoc.verification.MdocVerificationContext
import id.walt.mdoc.verification.MdocVerifier
import id.walt.mdoc.verification.verifyDeviceAuthentication
import id.walt.openid4vp.clientidprefix.ClientIdPrefixAuthenticator
import id.walt.openid4vp.clientidprefix.ClientIdPrefixParser
import id.walt.openid4vp.clientidprefix.ClientIdTrustConfiguration
import id.walt.openid4vp.clientidprefix.ClientValidationResult
import id.walt.openid4vp.clientidprefix.RequestContext
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
import kotlin.io.encoding.Base64
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
        private const val CLIENT_ID = "x509_hash:8hpjrKuE5Ob-_tveJ3rsvmLse250pLpzPPCqP-EKCNI"
        private const val DOCTYPE = "org.iso.18013.5.1.mDL"
        private const val TRUST_ANCHOR = "MIIBnTCCAUSgAwIBAgIUbDAeE4BA3sOLD+PEiilW4QOOGPwwCgYIKoZIzj0EAwIwGzEZMBcGA1UEAwwQREMgQVBJIFRlc3QgUm9vdDAeFw0yNjAxMDEwMDAwMDBaFw00NjAxMDEwMDAwMDBaMBsxGTAXBgNVBAMMEERDIEFQSSBUZXN0IFJvb3QwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAASSRBAldyf5gXI/YGffybDKBT3NuyPjDciWkEI6+xuAQIgedYgYaj0x1gmPR1pCj2xSvvX7Z349LIM2gqJLb4rfo2YwZDAdBgNVHQ4EFgQUQ5jLKJJklnh8rgV6y9o6k74SphAwHwYDVR0jBBgwFoAUQ5jLKJJklnh8rgV6y9o6k74SphAwEgYDVR0TAQH/BAgwBgEB/wIBADAOBgNVHQ8BAf8EBAMCAAYwCgYIKoZIzj0EAwIDRwAwRAIgXcUGnaXUnjswzkLYnqSMDD6lJVsNgbhRwowXSJOZ4bsCIEW+eViobOoCDjlIN6nG/8pqpm9xhgMUK8BB0OAvzOUj"

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
                    {"path": ["org.iso.18013.5.1", "given_name"]},
                    {"path": ["org.iso.18013.5.1", "age_over_21"]}
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
                    put("deviceauth_alg_values", JsonArray(listOf(JsonPrimitive(-7))))
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
            creationDate = Instant.parse("2030-01-03T00:00:00Z"),
            expirationDate = Instant.parse("2030-01-03T00:05:00Z"),
            retentionDate = Instant.parse("2040-01-03T00:00:00Z"),
            status = Verification2Session.VerificationSessionStatus.UNUSED,
            authorizationRequest = authorizationRequest,
            authorizationRequestUrl = authorizationRequest.toHttpUrl(URLBuilder(ORIGIN)),
            signedAuthorizationRequestJwt = SIGNED_AUTHORIZATION_REQUEST_JWT,
            requestMode = RequestMode.REQUEST_URI_SIGNED,
            policies = policies,
        )

        private const val SIGNED_AUTHORIZATION_REQUEST_JWT = "eyJ4NWMiOlsiTUlJQnN6Q0NBVnFnQXdJQkFnSVVSU1ZwVzNNR25IUktLcjNTMmZwcmpKUUZsUzR3Q2dZSUtvWkl6ajBFQXdJd0d6RVpNQmNHQTFVRUF3d1FSRU1nUVZCSklGUmxjM1FnVW05dmREQWVGdzB5TmpBeE1ERXdNREF3TURCYUZ3MDBOakF4TURFd01EQXdNREJhTUNJeElEQWVCZ05WQkFNTUYyUmpMV0Z3YVM1MlpYSnBabWxsY2k1bGVHRnRjR3hsTUZrd0V3WUhLb1pJemowQ0FRWUlLb1pJemowREFRY0RRZ0FFUFVTV2w5dlQ3S1Fkd2tDNWZwdFE2TDJ4SkpGaHowY0d1UWsvc0w2K1hGcXl5RDRYYU9YS2UxaDV2UE81N3BoRWdFK2swRzB6aExZbEpBaGJuUHFXQ3FOMU1ITXdIUVlEVlIwT0JCWUVGTmZHWm5OU2tFbVlqNEVHb21mVVVMalVlZ0tZTUI4R0ExVWRJd1FZTUJhQUZFT1l5eWlTWkpaNGZLNEZlc3ZhT3BPK0VxWVFNQXdHQTFVZEV3RUIvd1FDTUFBd0RnWURWUjBQQVFIL0JBUURBZ0NBTUJNR0ExVWRKUVFNTUFvR0NDc0dBUVVGQndNQ01Bb0dDQ3FHU000OUJBTUNBMGNBTUVRQ0lESFJmNndZWk9pTEVZYnVoNlBkdTArRFVwdUlUeS85Mkw2dFBKZzU5YmRkQWlCK2dOWU9CUDN6Q0pRKzc0UWNYQUVnZHZNY2FTOHNHWTRwcU9jOUZMN0NsUT09IiwiTUlJQm5UQ0NBVVNnQXdJQkFnSVViREFlRTRCQTNzT0xEK1BFaWlsVzRRT09HUHd3Q2dZSUtvWkl6ajBFQXdJd0d6RVpNQmNHQTFVRUF3d1FSRU1nUVZCSklGUmxjM1FnVW05dmREQWVGdzB5TmpBeE1ERXdNREF3TURCYUZ3MDBOakF4TURFd01EQXdNREJhTUJzeEdUQVhCZ05WQkFNTUVFUkRJRUZRU1NCVVpYTjBJRkp2YjNRd1dUQVRCZ2NxaGtqT1BRSUJCZ2dxaGtqT1BRTUJCd05DQUFTU1JCQWxkeWY1Z1hJL1lHZmZ5YkRLQlQzTnV5UGpEY2lXa0VJNit4dUFRSWdlZFlnWWFqMHgxZ21QUjFwQ2oyeFN2dlg3WjM0OUxJTTJncUpMYjRyZm8yWXdaREFkQmdOVkhRNEVGZ1FVUTVqTEtKSmtsbmg4cmdWNnk5bzZrNzRTcGhBd0h3WURWUjBqQkJnd0ZvQVVRNWpMS0pKa2xuaDhyZ1Y2eTlvNms3NFNwaEF3RWdZRFZSMFRBUUgvQkFnd0JnRUIvd0lCQURBT0JnTlZIUThCQWY4RUJBTUNBQVl3Q2dZSUtvWkl6ajBFQXdJRFJ3QXdSQUlnWGNVR25hWFVuanN3emtMWW5xU01ERDZsSlZzTmdiaFJ3b3dYU0pPWjRic0NJRVcrZVZpb2JPb0NEamxJTjZuRy84cHFwbTl4aGdNVUs4QkIwT0F2ek9VaiJdLCJ0eXAiOiJvYXV0aC1hdXRoei1yZXErand0IiwiYWxnIjoiRVMyNTYifQ.eyJyZXNwb25zZV90eXBlIjoidnBfdG9rZW4iLCJjbGllbnRfaWQiOiJ4NTA5X2hhc2g6OGhwanJLdUU1T2ItX3R2ZUozcnN2bUxzZTI1MHBMcHpQUENxUC1FS0NOSSIsInJlc3BvbnNlX21vZGUiOiJkY19hcGkiLCJub25jZSI6ImI5Y2MyODM3LWZiZTItNGMyYy1hMDQ3LTc1MDA3MWFhMDA2MyIsImRjcWxfcXVlcnkiOnsiY3JlZGVudGlhbHMiOlt7ImlkIjoibXlfbWRsIiwiZm9ybWF0IjoibXNvX21kb2MiLCJtZXRhIjp7ImRvY3R5cGVfdmFsdWUiOiJvcmcuaXNvLjE4MDEzLjUuMS5tREwifSwiY2xhaW1zIjpbeyJwYXRoIjpbIm9yZy5pc28uMTgwMTMuNS4xIiwiZmFtaWx5X25hbWUiXX0seyJwYXRoIjpbIm9yZy5pc28uMTgwMTMuNS4xIiwiZ2l2ZW5fbmFtZSJdfSx7InBhdGgiOlsib3JnLmlzby4xODAxMy41LjEiLCJhZ2Vfb3Zlcl8yMSJdfV19XX0sImNsaWVudF9tZXRhZGF0YSI6eyJ2cF9mb3JtYXRzX3N1cHBvcnRlZCI6eyJtc29fbWRvYyI6eyJpc3N1ZXJhdXRoX2FsZ192YWx1ZXMiOlstOV0sImRldmljZWF1dGhfYWxnX3ZhbHVlcyI6Wy03XX19fSwiZXhwZWN0ZWRfb3JpZ2lucyI6WyJodHRwczovL3BvcnRhbDIuZGVtby53YWx0LmlkIl19.-UG90PL5bGuYjRuCswWJ5ODpwpaEGDZCgRaRGixaS5fOwjSvRFv9k4kyziU5w6i9gdsrV5hUah0sAJ_z34hvLQ"
        private const val VP_TOKEN = "o2d2ZXJzaW9uYzEuMGlkb2N1bWVudHOBo2dkb2NUeXBldW9yZy5pc28uMTgwMTMuNS4xLm1ETGxpc3N1ZXJTaWduZWSiam5hbWVTcGFjZXOhcW9yZy5pc28uMTgwMTMuNS4xg9gYWFukaGRpZ2VzdElEAGZyYW5kb21YGMDcl9ZXHjjPaPEnLMrIvI8f8cW4LNtP_3FlbGVtZW50SWRlbnRpZmllcmtmYW1pbHlfbmFtZWxlbGVtZW50VmFsdWVjRG9l2BhYW6RoZGlnZXN0SUQBZnJhbmRvbVgYcModCIQ_iB0AB-mMw2fgOFdWpnv-lpEscWVsZW1lbnRJZGVudGlmaWVyamdpdmVuX25hbWVsZWxlbWVudFZhbHVlZEpvaG7YGFhYpGhkaWdlc3RJRAJmcmFuZG9tWBjKMceCN9qNkGUIwr4YdpDCVb4Kcoy06NVxZWxlbWVudElkZW50aWZpZXJrYWdlX292ZXJfMjFsZWxlbWVudFZhbHVl9Wppc3N1ZXJBdXRohEOhASahGCFZAg0wggIJMIIBsKADAgECAhRwQ1AD4I6o5sEnfTI03NBAbNjaxzAKBggqhkjOPQQDAjAoMQswCQYDVQQGEwJBVDEZMBcGA1UEAwwQV2FsdGlkIFRlc3QgSUFDQTAeFw0yNjAxMDEwMDAwMDBaFw00NjAxMDEwMDAwMDBaMDMxCzAJBgNVBAYTAkFUMSQwIgYDVQQDDBtXYWx0aWQgVGVzdCBEb2N1bWVudCBTaWduZXIwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAARdZw-2TXCu2QQ62ckd64WkVPNffONws5vamIwjM2xSFCaY9UmgbnoQqep7UnQw6VPtgerfcrIC3MWOudT3OsAMo4GsMIGpMB8GA1UdIwQYMBaAFNd43yJLdHdc55gkB9DihVG54JPwMB0GA1UdDgQWBBQkUdL5IKCjkHo9GpSknTiwi6QbOjAOBgNVHQ8BAf8EBAMCAIAwGgYDVR0SBBMwEYYPaHR0cHM6Ly93YWx0LmlkMBUGA1UdJQEB_wQLMAkGByiBjF0FAQIwJAYDVR0fBB0wGzAZoBegFYYTaHR0cHM6Ly93YWx0LmlkL2NybDAKBggqhkjOPQQDAgNHADBEAiAWSZKNaxQsYch-Ly4wshGb-NcYQrduVQ3gpruyebyINwIgRiu0yMV3FonvpCvewtj90VLiiSaStuWl1RnmG0pnUSxZAaLYGFkBnaZndmVyc2lvbmMxLjBvZGlnZXN0QWxnb3JpdGhtZ1NIQS0yNTZsdmFsdWVEaWdlc3RzoXFvcmcuaXNvLjE4MDEzLjUuMaMAWCDRUp-2dPYC-2PcsaDauH15ZwQ7jWUNtpv1zv6Zsp06uQFYIMcaRI4ye8dVdBNpljl77ZlxI1OGwMH_aA8pCxaIsnITAlggukEF-IaX63r8KZ2Sby91QXw2G5wwaR7vJCtJP8-1i4NtZGV2aWNlS2V5SW5mb6FpZGV2aWNlS2V5pAECIAEhWCB5NPZZ3OWY5YEi12BKayoHX8E_r3B-7A57Uhi9pjVZ0iJYIMGzrtxj9NiYjlSEUP7n1i0TTpgwerHmAGmwprBgX6_-Z2RvY1R5cGV1b3JnLmlzby4xODAxMy41LjEubURMbHZhbGlkaXR5SW5mb6Nmc2lnbmVkwHQyMDI2LTA4LTE0VDEwOjE3OjI0Wml2YWxpZEZyb23AdDIwMjYtMDgtMTRUMTA6MTc6MjRaanZhbGlkVW50aWzAdDIwNDUtMTItMzFUMDA6MDA6MDBaWEDxMDTchs9AWrPmB_RVPaHt6-twlN_gp55u3amLkCPT4JGngZYqMmHiDHjXGrglHjpFC1ZbIeAYY1fpi0FHk9SBbGRldmljZVNpZ25lZKJqbmFtZVNwYWNlc9gYQaBqZGV2aWNlQXV0aKFvZGV2aWNlU2lnbmF0dXJlhEOhASag9lhAo80Xl7Ef_wBhtfNMln4PhVp7tqf-LirVbgTTP-3_A0xJTwtnn9LHmULiWtTxV0gFPxeObSQqTls2_GOtkn839GZzdGF0dXMA"
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

        private fun session() = staticSession.copy(signedAuthorizationRequestJwt = SIGNED_AUTHORIZATION_REQUEST_JWT)
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
        assertEquals("vp_token", jwtPayload["response_type"]!!.jsonPrimitive.content)
        assertEquals(CLIENT_ID, jwtPayload["client_id"]!!.jsonPrimitive.content)
        assertEquals("dc_api", jwtPayload["response_mode"]!!.jsonPrimitive.content)
        assertEquals(listOf(ORIGIN), jwtPayload["expected_origins"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(null, jwtPayload["state"])
        assertEquals(NONCE, jwtPayload["nonce"]!!.jsonPrimitive.content)
        assertEquals(
            Json.encodeToJsonElement(id.walt.dcql.models.DcqlQuery.serializer(), session.authorizationRequest.dcqlQuery!!),
            jwtPayload["dcql_query"],
        )
        assertEquals(Json.encodeToJsonElement(ClientMetadata.serializer(), clientMetadata()), jwtPayload["client_metadata"])
    }

    @Test
    fun `x509 hash request object authenticates with its client-auth certificate chain`() = runTest {
        val result = ClientIdPrefixAuthenticator.authenticate(
            clientId = ClientIdPrefixParser.parse(CLIENT_ID).getOrThrow(),
            context = RequestContext(
                clientId = CLIENT_ID,
                clientMetadata = clientMetadata(),
                requestObjectJws = SIGNED_AUTHORIZATION_REQUEST_JWT,
            ),
            preRegisteredMetadataProvider = { null },
            trustConfiguration = ClientIdTrustConfiguration(
                x509TrustAnchors = listOf(
                    id.walt.x509.CertificateDer(Base64.Default.decode(TRUST_ANCHOR)),
                ),
            ),
        )
        assertIs<ClientValidationResult.Success>(result)
    }

    @Test
    fun `static mdoc device auth is signed for the declared DC API origin`() = runTest {
        val document = presentedDocument()
        val transcript = MdocVerifier.buildSessionTranscriptForContext(
            MdocVerificationContext(
                expectedNonce = NONCE,
                expectedAudience = ORIGIN,
                responseUri = null,
                jwkThumbprint = session().jwkThumbprint,
                isDcApi = true,
            ),
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
            updateSessionCallback = { _, _, _ -> },
            failSessionCallback = { _, _, _ -> },
            verificationTime = Instant.parse("2030-01-03T00:01:00Z"),
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
                verificationTime = Instant.parse("2030-01-03T00:01:00Z"),
            )
        }
    }
}
