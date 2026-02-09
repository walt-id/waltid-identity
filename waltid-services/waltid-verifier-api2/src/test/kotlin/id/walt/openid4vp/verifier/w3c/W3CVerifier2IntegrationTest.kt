package id.walt.openid4vp.verifier.w3c

import id.walt.commons.config.ConfigManager
import id.walt.commons.testing.E2ETest
import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.signatures.sdjwt.SelectivelyDisclosableVerifiableCredential
import id.walt.crypto.keys.KeyManager
import id.walt.dcql.DcqlDisclosure
import id.walt.dcql.DcqlMatcher
import id.walt.dcql.RawDcqlCredential
import id.walt.dcql.models.ClaimsQuery
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.meta.JwtVcJsonMeta
import id.walt.did.dids.DidService
import id.walt.did.dids.resolver.LocalResolver
import id.walt.openid4vp.verifier.OSSVerifier2FeatureCatalog
import id.walt.openid4vp.verifier.OSSVerifier2ServiceConfig
import id.walt.openid4vp.verifier.data.CrossDeviceFlowSetup
import id.walt.openid4vp.verifier.data.GeneralFlowConfig
import id.walt.openid4vp.verifier.data.Verification2Session
import id.walt.openid4vp.verifier.data.VerificationSessionSetup
import id.walt.openid4vp.verifier.handlers.sessioncreation.VerificationSessionCreator
import id.walt.openid4vp.verifier.verifierModule
import id.walt.policies2.vc.VCPolicyList
import id.walt.policies2.vc.policies.CredentialSignaturePolicy
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.waltid.openid4vp.wallet.WalletPresentFunctionality2
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.server.application.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class W3CVerifier2IntegrationTest {

    private val w3cDcqlQuery = DcqlQuery(
        credentials = listOf(
            CredentialQuery(
                id = "openbadge",
                format = CredentialFormat.JWT_VC_JSON,
                meta = JwtVcJsonMeta(
                    typeValues = listOf(listOf("VerifiableCredential", "OpenBadgeCredential"))
                ),
                claims = listOf(
                    //         ClaimsQuery(path = listOf("issuer", "name") ),
                    ClaimsQuery(path = listOf("credentialSubject", "achievement", "name")),
                    //ClaimsQuery(path = listOf("credentialSubject", "achievement", "description", "narrative")),
                )
            ),
            CredentialQuery(
                id = "universitydegree",
                format = CredentialFormat.JWT_VC_JSON,
                meta = JwtVcJsonMeta(
                    typeValues = listOf(listOf("VerifiableCredential", "UniversityDegreeCredential"))
                ),
                claims = listOf(
                    ClaimsQuery(path = listOf("issuer", "id")),
                    ClaimsQuery(path = listOf("credentialSubject", "id")),
                    ClaimsQuery(path = listOf("credentialSubject", "degree", "type")),
                )
            )
        )
    )

    private val w3cPolicies = Verification2Session.DefinedVerificationPolicies(
        vc_policies = VCPolicyList(
            listOf(
                CredentialSignaturePolicy()
            )
        )
    )

    private val verificationSessionSetup: VerificationSessionSetup = CrossDeviceFlowSetup(
        core = GeneralFlowConfig(
            dcqlQuery = w3cDcqlQuery,
            policies = w3cPolicies
        )
    )

    private val walletCredentials = listOf(
        Json.decodeFromString<DigitalCredential>(
            """
            {
      "type": "vc-w3c_1_1",
      "disclosables": {},
      "credentialData": {
        "@context": [
          "https://www.w3.org/2018/credentials/v1",
          "https://purl.imsglobal.org/spec/ob/v3p0/context.json"
        ],
        "id": "urn:uuid:98a8e420-d85e-4a1f-9a2b-d99a89c42bb4",
        "type": [
          "VerifiableCredential",
          "OpenBadgeCredential"
        ],
        "name": "JFF x vc-edu PlugFest 3 Interoperability",
        "issuer": {
          "type": [
            "Profile"
          ],
          "name": "Jobs for the Future (JFF)",
          "url": "https://www.jff.org/",
          "image": "https://w3c-ccg.github.io/vc-ed/plugfest-1-2022/images/JFF_LogoLockup.png",
          "id": "did:key:z6MkjoRhq1jSNJdLiruSXrFFxagqrztZaXHqHGUTKJbcNywp"
        },
        "credentialSubject": {
          "type": [
            "AchievementSubject"
          ],
          "achievement": {
            "id": "urn:uuid:ac254bd5-8fad-4bb1-9d29-efd938536926",
            "type": [
              "Achievement"
            ],
            "name": "JFF x vc-edu PlugFest 3 Interoperability",
            "description": "This wallet supports the use of W3C Verifiable Credentials and has demonstrated interoperability during the presentation request workflow during JFF x VC-EDU PlugFest 3.",
            "criteria": {
              "type": "Criteria",
              "narrative": "Wallet solutions providers earned this badge by demonstrating interoperability during the presentation request workflow. This includes successfully receiving a presentation request, allowing the holder to select at least two types of verifiable credentials to create a verifiable presentation, returning the presentation to the requestor, and passing verification of the presentation and the included credentials."
            },
            "image": {
              "id": "https://w3c-ccg.github.io/vc-ed/plugfest-3-2023/images/JFF-VC-EDU-PLUGFEST3-badge-image.png",
              "type": "Image"
            }
          },
          "id": "did:key:zDnaeYb7DakQWmYkrLkmsVERAazF5Ya1G5nxbSnQcLJZ8Cr17"
        },
        "issuanceDate": "2025-10-20T06:22:37.444427698Z",
        "expirationDate": "2026-10-20T06:22:37.444457898Z"
      },
      "issuer": "did:key:z6MkjoRhq1jSNJdLiruSXrFFxagqrztZaXHqHGUTKJbcNywp",
      "subject": "did:key:zDnaeYb7DakQWmYkrLkmsVERAazF5Ya1G5nxbSnQcLJZ8Cr17",
      "signature": {
        "type": "signature-jwt",
        "signature": "GETFmX9JOGDmTe8k3t1i4gVA3PzQGi_WNb6zXEIxavoZSYsxJcyiJ8pZ_jEjvIUFfFPBvJLVJqb4Mcgwc09fAQ",
        "jwtHeader": {
          "kid": "did:key:z6MkjoRhq1jSNJdLiruSXrFFxagqrztZaXHqHGUTKJbcNywp#z6MkjoRhq1jSNJdLiruSXrFFxagqrztZaXHqHGUTKJbcNywp",
          "typ": "JWT",
          "alg": "EdDSA"
        }
      },
      "signed": "eyJraWQiOiJkaWQ6a2V5Ono2TWtqb1JocTFqU05KZExpcnVTWHJGRnhhZ3FyenRaYVhIcUhHVVRLSmJjTnl3cCN6Nk1ram9SaHExalNOSmRMaXJ1U1hyRkZ4YWdxcnp0WmFYSHFIR1VUS0piY055d3AiLCJ0eXAiOiJKV1QiLCJhbGciOiJFZERTQSJ9.eyJpc3MiOiJkaWQ6a2V5Ono2TWtqb1JocTFqU05KZExpcnVTWHJGRnhhZ3FyenRaYVhIcUhHVVRLSmJjTnl3cCIsInN1YiI6ImRpZDprZXk6ekRuYWVZYjdEYWtRV21Za3JMa21zVkVSQWF6RjVZYTFHNW54YlNuUWNMSlo4Q3IxNyIsInZjIjp7IkBjb250ZXh0IjpbImh0dHBzOi8vd3d3LnczLm9yZy8yMDE4L2NyZWRlbnRpYWxzL3YxIiwiaHR0cHM6Ly9wdXJsLmltc2dsb2JhbC5vcmcvc3BlYy9vYi92M3AwL2NvbnRleHQuanNvbiJdLCJpZCI6InVybjp1dWlkOjk4YThlNDIwLWQ4NWUtNGExZi05YTJiLWQ5OWE4OWM0MmJiNCIsInR5cGUiOlsiVmVyaWZpYWJsZUNyZWRlbnRpYWwiLCJPcGVuQmFkZ2VDcmVkZW50aWFsIl0sIm5hbWUiOiJKRkYgeCB2Yy1lZHUgUGx1Z0Zlc3QgMyBJbnRlcm9wZXJhYmlsaXR5IiwiaXNzdWVyIjp7InR5cGUiOlsiUHJvZmlsZSJdLCJuYW1lIjoiSm9icyBmb3IgdGhlIEZ1dHVyZSAoSkZGKSIsInVybCI6Imh0dHBzOi8vd3d3LmpmZi5vcmcvIiwiaW1hZ2UiOiJodHRwczovL3czYy1jY2cuZ2l0aHViLmlvL3ZjLWVkL3BsdWdmZXN0LTEtMjAyMi9pbWFnZXMvSkZGX0xvZ29Mb2NrdXAucG5nIiwiaWQiOiJkaWQ6a2V5Ono2TWtqb1JocTFqU05KZExpcnVTWHJGRnhhZ3FyenRaYVhIcUhHVVRLSmJjTnl3cCJ9LCJjcmVkZW50aWFsU3ViamVjdCI6eyJ0eXBlIjpbIkFjaGlldmVtZW50U3ViamVjdCJdLCJhY2hpZXZlbWVudCI6eyJpZCI6InVybjp1dWlkOmFjMjU0YmQ1LThmYWQtNGJiMS05ZDI5LWVmZDkzODUzNjkyNiIsInR5cGUiOlsiQWNoaWV2ZW1lbnQiXSwibmFtZSI6IkpGRiB4IHZjLWVkdSBQbHVnRmVzdCAzIEludGVyb3BlcmFiaWxpdHkiLCJkZXNjcmlwdGlvbiI6IlRoaXMgd2FsbGV0IHN1cHBvcnRzIHRoZSB1c2Ugb2YgVzNDIFZlcmlmaWFibGUgQ3JlZGVudGlhbHMgYW5kIGhhcyBkZW1vbnN0cmF0ZWQgaW50ZXJvcGVyYWJpbGl0eSBkdXJpbmcgdGhlIHByZXNlbnRhdGlvbiByZXF1ZXN0IHdvcmtmbG93IGR1cmluZyBKRkYgeCBWQy1FRFUgUGx1Z0Zlc3QgMy4iLCJjcml0ZXJpYSI6eyJ0eXBlIjoiQ3JpdGVyaWEiLCJuYXJyYXRpdmUiOiJXYWxsZXQgc29sdXRpb25zIHByb3ZpZGVycyBlYXJuZWQgdGhpcyBiYWRnZSBieSBkZW1vbnN0cmF0aW5nIGludGVyb3BlcmFiaWxpdHkgZHVyaW5nIHRoZSBwcmVzZW50YXRpb24gcmVxdWVzdCB3b3JrZmxvdy4gVGhpcyBpbmNsdWRlcyBzdWNjZXNzZnVsbHkgcmVjZWl2aW5nIGEgcHJlc2VudGF0aW9uIHJlcXVlc3QsIGFsbG93aW5nIHRoZSBob2xkZXIgdG8gc2VsZWN0IGF0IGxlYXN0IHR3byB0eXBlcyBvZiB2ZXJpZmlhYmxlIGNyZWRlbnRpYWxzIHRvIGNyZWF0ZSBhIHZlcmlmaWFibGUgcHJlc2VudGF0aW9uLCByZXR1cm5pbmcgdGhlIHByZXNlbnRhdGlvbiB0byB0aGUgcmVxdWVzdG9yLCBhbmQgcGFzc2luZyB2ZXJpZmljYXRpb24gb2YgdGhlIHByZXNlbnRhdGlvbiBhbmQgdGhlIGluY2x1ZGVkIGNyZWRlbnRpYWxzLiJ9LCJpbWFnZSI6eyJpZCI6Imh0dHBzOi8vdzNjLWNjZy5naXRodWIuaW8vdmMtZWQvcGx1Z2Zlc3QtMy0yMDIzL2ltYWdlcy9KRkYtVkMtRURVLVBMVUdGRVNUMy1iYWRnZS1pbWFnZS5wbmciLCJ0eXBlIjoiSW1hZ2UifX0sImlkIjoiZGlkOmtleTp6RG5hZVliN0Rha1FXbVlrckxrbXNWRVJBYXpGNVlhMUc1bnhiU25RY0xKWjhDcjE3In0sImlzc3VhbmNlRGF0ZSI6IjIwMjUtMTAtMjBUMDY6MjI6MzcuNDQ0NDI3Njk4WiIsImV4cGlyYXRpb25EYXRlIjoiMjAyNi0xMC0yMFQwNjoyMjozNy40NDQ0NTc4OThaIn0sImp0aSI6InVybjp1dWlkOjk4YThlNDIwLWQ4NWUtNGExZi05YTJiLWQ5OWE4OWM0MmJiNCIsImV4cCI6MTc5MjQ3NzM1NywiaWF0IjoxNzYwOTQxMzU3LCJuYmYiOjE3NjA5NDEzNTd9.GETFmX9JOGDmTe8k3t1i4gVA3PzQGi_WNb6zXEIxavoZSYsxJcyiJ8pZ_jEjvIUFfFPBvJLVJqb4Mcgwc09fAQ",
      "format": "jwt_vc_json"
    }
        """.trimIndent()
        ),

        Json.decodeFromString<DigitalCredential>(
            """
            {
      "type": "vc-w3c_1_1",
      "disclosables": {},
      "credentialData": {
        "@context": [
          "https://www.w3.org/2018/credentials/v1",
          "https://www.w3.org/2018/credentials/examples/v1"
        ],
        "id": "urn:uuid:cb305da6-5674-484b-b12d-533240ec0129",
        "type": [
          "VerifiableCredential",
          "UniversityDegreeCredential"
        ],
        "issuer": {
          "id": "did:key:z6MkjoRhq1jSNJdLiruSXrFFxagqrztZaXHqHGUTKJbcNywp"
        },
        "issuanceDate": "2025-10-20T06:22:58.235375395Z",
        "credentialSubject": {
          "id": "did:key:zDnaeYb7DakQWmYkrLkmsVERAazF5Ya1G5nxbSnQcLJZ8Cr17",
          "degree": {
            "type": "BachelorDegree",
            "name": "Bachelor of Science and Arts"
          }
        },
        "expirationDate": "2026-10-20T06:22:58.235404795Z"
      },
      "issuer": "did:key:z6MkjoRhq1jSNJdLiruSXrFFxagqrztZaXHqHGUTKJbcNywp",
      "subject": "did:key:zDnaeYb7DakQWmYkrLkmsVERAazF5Ya1G5nxbSnQcLJZ8Cr17",
      "signature": {
        "type": "signature-jwt",
        "signature": "iEQT-2QvZu-qjUwtU8Ok9ie8SgscU2jbNaO6FsngLdhyUfIdUbYNqi9RQwT8Ht0otskKb08YrjEkVR9cvBLGCw",
        "jwtHeader": {
          "kid": "did:key:z6MkjoRhq1jSNJdLiruSXrFFxagqrztZaXHqHGUTKJbcNywp#z6MkjoRhq1jSNJdLiruSXrFFxagqrztZaXHqHGUTKJbcNywp",
          "typ": "JWT",
          "alg": "EdDSA"
        }
      },
      "signed": "eyJraWQiOiJkaWQ6a2V5Ono2TWtqb1JocTFqU05KZExpcnVTWHJGRnhhZ3FyenRaYVhIcUhHVVRLSmJjTnl3cCN6Nk1ram9SaHExalNOSmRMaXJ1U1hyRkZ4YWdxcnp0WmFYSHFIR1VUS0piY055d3AiLCJ0eXAiOiJKV1QiLCJhbGciOiJFZERTQSJ9.eyJpc3MiOiJkaWQ6a2V5Ono2TWtqb1JocTFqU05KZExpcnVTWHJGRnhhZ3FyenRaYVhIcUhHVVRLSmJjTnl3cCIsInN1YiI6ImRpZDprZXk6ekRuYWVZYjdEYWtRV21Za3JMa21zVkVSQWF6RjVZYTFHNW54YlNuUWNMSlo4Q3IxNyIsInZjIjp7IkBjb250ZXh0IjpbImh0dHBzOi8vd3d3LnczLm9yZy8yMDE4L2NyZWRlbnRpYWxzL3YxIiwiaHR0cHM6Ly93d3cudzMub3JnLzIwMTgvY3JlZGVudGlhbHMvZXhhbXBsZXMvdjEiXSwiaWQiOiJ1cm46dXVpZDpjYjMwNWRhNi01Njc0LTQ4NGItYjEyZC01MzMyNDBlYzAxMjkiLCJ0eXBlIjpbIlZlcmlmaWFibGVDcmVkZW50aWFsIiwiVW5pdmVyc2l0eURlZ3JlZUNyZWRlbnRpYWwiXSwiaXNzdWVyIjp7ImlkIjoiZGlkOmtleTp6Nk1ram9SaHExalNOSmRMaXJ1U1hyRkZ4YWdxcnp0WmFYSHFIR1VUS0piY055d3AifSwiaXNzdWFuY2VEYXRlIjoiMjAyNS0xMC0yMFQwNjoyMjo1OC4yMzUzNzUzOTVaIiwiY3JlZGVudGlhbFN1YmplY3QiOnsiaWQiOiJkaWQ6a2V5OnpEbmFlWWI3RGFrUVdtWWtyTGttc1ZFUkFhekY1WWExRzVueGJTblFjTEpaOENyMTciLCJkZWdyZWUiOnsidHlwZSI6IkJhY2hlbG9yRGVncmVlIiwibmFtZSI6IkJhY2hlbG9yIG9mIFNjaWVuY2UgYW5kIEFydHMifX0sImV4cGlyYXRpb25EYXRlIjoiMjAyNi0xMC0yMFQwNjoyMjo1OC4yMzU0MDQ3OTVaIn0sImp0aSI6InVybjp1dWlkOmNiMzA1ZGE2LTU2NzQtNDg0Yi1iMTJkLTUzMzI0MGVjMDEyOSIsImV4cCI6MTc5MjQ3NzM3OCwiaWF0IjoxNzYwOTQxMzc4LCJuYmYiOjE3NjA5NDEzNzh9.iEQT-2QvZu-qjUwtU8Ok9ie8SgscU2jbNaO6FsngLdhyUfIdUbYNqi9RQwT8Ht0otskKb08YrjEkVR9cvBLGCw",
      "format": "jwt_vc_json"
    }
        """.trimIndent()

        )
    )

    private val holderKeyFun = suspend {
        KeyManager.resolveSerializedKey(
            """
        {
            "type": "jwk",
            "jwk": {
              "kty": "EC",
              "d": "QN9Y3k_3Hy2OV0C5Pmez_ObEXJKcXonnMg3xTpcLOAg",
              "crv": "P-256",
              "kid": "KmQ8TOSmhg1UV9nQfQaTQ5wwbHrEgOENvJ_3AlEriAw",
              "x": "eTT2WdzlmOWBItdgSmsqB1_BP69wfuwOe1IYvaY1WdI",
              "y": "wbOu3GP02JiOVIRQ_ufWLRNOmDB6seYAabCmsGBfr_4"
            }
          }
    """.trimIndent()
        )
    }

    private val holderDid = "did:key:zDnaeYb7DakQWmYkrLkmsVERAazF5Ya1G5nxbSnQcLJZ8Cr17"

    private suspend fun selectCredentialsForQuery(
        query: DcqlQuery,
    ): Map<String, List<DcqlMatcher.DcqlMatchResult>> {
        val storedCredentials = walletCredentials

        val dcqlCredentials = storedCredentials.mapIndexed { idx, credential ->
            RawDcqlCredential(
                id = idx.toString(),
                format = credential.format,
                data = credential.credentialData,
                originalCredential = credential,
                disclosures = if (credential is SelectivelyDisclosableVerifiableCredential)
                    credential.disclosures?.map { DcqlDisclosure(it.name, it.value) }
                else null
            )
        }

        val matched = DcqlMatcher.match(query, dcqlCredentials).getOrThrow()
        if (matched.isEmpty()) {
            throw IllegalArgumentException("No matching credential")
        }

        return matched
    }

    @Test
    fun test() {
        val host = "127.0.0.1"
        val port = 17031

        E2ETest(host, port, true).testBlock(
            features = listOf(OSSVerifier2FeatureCatalog),
            preload = {
                ConfigManager.preloadConfig(
                    "verifier-service", OSSVerifier2ServiceConfig(
                        clientId = "verifier2",
                        clientMetadata = ClientMetadata(
                            clientName = "Verifier2",
                            logoUri = "https://images.squarespace-cdn.com/content/v1/609c0ddf94bcc0278a7cbdb4/4d493ccf-c893-4882-925f-fda3256c38f4/Walt.id_Logo_transparent.png"
                        ),
                        urlPrefix = "http://$host:$port/verification-session",
                        urlHost = "openid4vp://authorize"
                    )
                )
            },
            init = {
                DidService.apply {
                    registerResolver(LocalResolver())
                    updateResolversForMethods()
                }
            },
            module = Application::verifierModule
        ) {
            val http = testHttpClient()

            // Create the verification session
            val verificationSessionResponse = testAndReturn("Create verification session") {
                http.post("/verification-session/create") {
                    setBody(verificationSessionSetup)
                }.body<VerificationSessionCreator.VerificationSessionCreationResponse>()
            }
            println("Verification Session Response: $verificationSessionResponse")

            // Check verification session
            test("Check Verification Session Response") {
                assertTrue {
                    verificationSessionResponse.bootstrapAuthorizationRequestUrl.toString().length < verificationSessionResponse.fullAuthorizationRequestUrl.toString().length
                }
            }

            val sessionId = verificationSessionResponse.sessionId

            // View created session
            val info1 = testAndReturn("View created session") {
                http.get("/verification-session/$sessionId/info")
                    .body<Verification2Session>()
            }

            // Check created session
            test("Check Verification Session") {
                assertTrue {
                    info1.creationDate.wasWithinLastSeconds()
                }
            }

            // Present with wallet
            val bootstrapUrl = verificationSessionResponse.bootstrapAuthorizationRequestUrl

            val holderKey = holderKeyFun()

            val selectCallback: suspend (DcqlQuery) -> Map<String, List<DcqlMatcher.DcqlMatchResult>> = { query ->
                selectCredentialsForQuery(
                    query = query
                )
            }

            val presentationResult = testAndReturn("Present with wallet") {
                WalletPresentFunctionality2.walletPresentHandling(
                    holderKey = holderKey,
                    holderDid = holderDid,
                    presentationRequestUrl = bootstrapUrl!!,
                    selectCredentialsForQuery = selectCallback,
                    holderPoliciesToRun = null,
                    runPolicies = null
                )
            }

            println("Presentation result: $presentationResult")

            // Check presentation result by wallet
            test("Verify presentation result") {
                assertTrue { presentationResult.isSuccess }

                val resp = presentationResult.getOrThrow().jsonObject
                println(resp)
                assertTrue("Transmission did not succeed") { resp["transmission_success"]!!.jsonPrimitive.boolean }
                assertTrue { resp["verifier_response"]!!.jsonObject["status"]!!.jsonPrimitive.content == "received" }
            }


            // View session that was presented to
            val info2 = testAndReturn("View presented session") {
                http.get("/verification-session/$sessionId/info")
                    .body<Verification2Session>()
            }

            // Check created session
            test("Check Verification Session after presentation") {
                assertTrue { info2.attempted }
                assertTrue { info2.status == Verification2Session.VerificationSessionStatus.SUCCESSFUL }

                assertNotNull(info2.presentedCredentials)
                assertEquals(2, info2.presentedCredentials!!.size)
                assertNotNull(info2.presentedCredentials!!["openbadge"])
                assertEquals(1, info2.presentedCredentials!!["openbadge"]!!.size)
                assertNotNull(info2.presentedCredentials!!["universitydegree"])
                assertEquals(1, info2.presentedCredentials!!["universitydegree"]!!.size)

                assertNotNull(info2.policyResults)
                assertTrue { info2.policyResults!!.overallSuccess }
                assertEquals(2, info2.policyResults!!.vcPolicies.size)
            }
        }
    }

    // Utils:
    private fun Instant.wasWithinLastSeconds(): Boolean {
        val now = Clock.System.now()
        return this <= now && (now - this) <= 1500.milliseconds
    }
}
