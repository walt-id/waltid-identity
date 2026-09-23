package id.walt.verifier2.sdjwt

import id.walt.commons.config.ConfigManager
import id.walt.commons.testing.E2ETest
import id.walt.credentials.CredentialParser
import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.signatures.sdjwt.SelectivelyDisclosableVerifiableCredential
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.dcql.DcqlDisclosure
import id.walt.dcql.DcqlMatcher
import id.walt.dcql.RawDcqlCredential
import id.walt.dcql.models.ClaimsQuery
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.meta.SdJwtVcMeta
import id.walt.did.dids.DidService
import id.walt.did.dids.registrar.dids.DidKeyCreateOptions
import id.walt.did.dids.registrar.local.key.DidKeyRegistrar
import id.walt.did.dids.resolver.LocalResolver
import id.walt.ktornotifications.core.KtorSessionNotifications
import id.walt.policies2.vc.VCPolicyList
import id.walt.policies2.vc.policies.CredentialSignaturePolicy
import id.walt.sdjwt.SDJwtVC
import id.walt.sdjwt.SDMap
import id.walt.sdjwt.SDPayload
import id.walt.sdjwt.WaltIdJWTCryptoProvider
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.verifier.openid.transactiondata.TransactionDataTypeRegistry
import id.walt.verifier2.OSSVerifier2FeatureCatalog
import id.walt.verifier2.OSSVerifier2ServiceConfig
import id.walt.verifier2.data.CrossDeviceFlowSetup
import id.walt.verifier2.data.GeneralFlowConfig
import id.walt.verifier2.data.SessionEvent
import id.walt.verifier2.data.Verification2Session
import id.walt.verifier2.data.VerificationSessionSetup
import id.walt.verifier2.events.Verifier2WebhookRecorder
import id.walt.verifier2.handlers.sessioncreation.VerificationSessionCreationResponse
import id.walt.verifier2.verifierModule
import id.waltid.openid4vp.wallet.WalletPresentFunctionality2
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Url
import io.ktor.server.application.Application
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * Happy-path SD-JWT VC presentation against verifier2.
 *
 * Unlike [IETFSdJwtVcNoDisclosuresVerifier2IntegrationTest], this test mints a fresh
 * `dc+sd-jwt` at runtime so [WalletPresentFunctionality2] can attach a live key-binding JWT
 * whose `sd_hash` matches the presented token. That lets VP validation succeed and emit the
 * full success callback sequence.
 */
class IETFSdJwtVcHappyPathVerifier2IntegrationTest {

    private val identityCredentialVct = "https://issuer.example/identity_credential"

    private val sdJwtVcDcqlQuery = DcqlQuery(
        credentials = listOf(
            CredentialQuery(
                id = "my_pid",
                format = CredentialFormat.DC_SD_JWT,
                meta = SdJwtVcMeta(
                    vctValues = listOf(identityCredentialVct)
                ),
                claims = listOf(
                    ClaimsQuery(pathStrings = listOf("given_name")),
                    ClaimsQuery(pathStrings = listOf("family_name")),
                    ClaimsQuery(pathStrings = listOf("age_over_18")),
                )
            )
        )
    )

    private val sdjwtvcPolicies = Verification2Session.DefinedVerificationPolicies(
        vc_policies = VCPolicyList(
            listOf(
                CredentialSignaturePolicy()
            )
        )
    )

    private fun verificationSessionSetup(notifications: KtorSessionNotifications): VerificationSessionSetup =
        CrossDeviceFlowSetup(
            core = GeneralFlowConfig(
                dcqlQuery = sdJwtVcDcqlQuery,
                policies = sdjwtvcPolicies,
                notifications = notifications,
            ),
            redirects = Verification2Session.VerificationSessionRedirects(
                successRedirectUri = Url("https://example.com/success"),
            ),
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

    @Suppress("DEPRECATION")
    private suspend fun issueSdJwtVcForHolder(): DigitalCredential {
        val issuerKey = JWKKey.generate(KeyType.Ed25519)
        val issuerDid = DidKeyRegistrar().registerByKey(issuerKey, DidKeyCreateOptions()).did
        val issuerKeyId = issuerKey.getKeyId()
        val now = Clock.System.now()
        val claims = buildJsonObject {
            put("given_name", JsonPrimitive("Jane"))
            put("family_name", JsonPrimitive("Doe"))
            put("age_over_18", JsonPrimitive(true))
        }
        val sdJwtVc = SDJwtVC.sign(
            sdPayload = SDPayload.createSDPayload(claims, SDMap(emptyMap())),
            jwtCryptoProvider = WaltIdJWTCryptoProvider(mapOf(issuerKeyId to issuerKey)),
            issuerDid = issuerDid,
            holderDid = holderDid,
            issuerKeyId = issuerKeyId,
            vct = identityCredentialVct,
            nbf = now.epochSeconds,
            exp = (now + 365.days).epochSeconds,
            additionalJwtHeader = mapOf("kid" to issuerDid),
            subject = holderDid,
        )
        return CredentialParser.parseOnly(sdJwtVc.toString(formatForPresentation = false, withKBJwt = false))
    }

    private suspend fun selectCredentialsForQuery(
        query: DcqlQuery,
        storedCredentials: List<DigitalCredential>,
    ): Map<String, List<DcqlMatcher.DcqlMatchResult>> {
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
        val port = 17023
        Verifier2WebhookRecorder().start().use { webhook ->
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
            val walletCredential = issueSdJwtVcForHolder()

            val verificationSessionResponse = testAndReturn("Create verification session") {
                http.post("/verification-session/create") {
                    setBody(verificationSessionSetup(webhook.notifications()))
                }.body<VerificationSessionCreationResponse>()
            }
            println("Verification Session Response: $verificationSessionResponse")

            test("Check Verification Session Response") {
                assertTrue {
                    verificationSessionResponse.bootstrapAuthorizationRequestUrl.toString().length < verificationSessionResponse.fullAuthorizationRequestUrl.toString().length
                }
            }

            val sessionId = verificationSessionResponse.sessionId

            val info1 = testAndReturn("View created session") {
                http.get("/verification-session/$sessionId/info")
                    .body<Verification2Session>()
            }

            test("Check Verification Session") {
                assertTrue {
                    info1.creationDate.wasWithinLastSeconds()
                }
            }

            val bootstrapUrl = verificationSessionResponse.bootstrapAuthorizationRequestUrl
            val holderKey = holderKeyFun()
            val selectCallback: suspend (DcqlQuery) -> Map<String, List<DcqlMatcher.DcqlMatchResult>> = { query ->
                selectCredentialsForQuery(query, listOf(walletCredential))
            }

            val presentationResult = testAndReturn("Present with wallet") {
                WalletPresentFunctionality2.walletPresentHandling(
                    holderKey = holderKey,
                    holderDid = holderDid,
                    presentationRequestUrl = bootstrapUrl!!,
                    selectCredentialsForQuery = selectCallback,
                    holderPoliciesToRun = null,
                    runPolicies = null,
                    transactionDataTypeRegistry = TransactionDataTypeRegistry(emptySet()),
                )
            }

            println("Presentation result: $presentationResult")

            test("Verify presentation result") {
                assertTrue { presentationResult.isSuccess }

                val resp = presentationResult.getOrThrow()
                println(resp)
                assertTrue("Transmission did not succeed") { resp.transmissionSuccess == true }
                val redirectUri = resp.verifierResponse!!.jsonObject["redirect_uri"]!!.jsonPrimitive.content
                assertTrue(redirectUri.startsWith("https://example.com/success"))
                assertTrue(redirectUri.contains("response_code="))
            }

            val info2 = testAndReturn("View presented session") {
                http.get("/verification-session/$sessionId/info")
                    .body<Verification2Session>()
            }

            test("Check Verification Session after presentation") {
                assertTrue { info2.attempted }
                assertTrue { info2.status == Verification2Session.VerificationSessionStatus.SUCCESSFUL }

                assertNotNull(info2.presentedCredentials)
                assertEquals(1, info2.presentedCredentials!!.size)
                assertNotNull(info2.presentedCredentials!!["my_pid"])
                assertEquals(1, info2.presentedCredentials!!["my_pid"]!!.size)

                assertNotNull(info2.policyResults)
                assertTrue { info2.policyResults!!.overallSuccess }
                assertEquals(1, info2.policyResults!!.vcPolicies.size)
                assertEquals("my_pid", info2.policyResults!!.vcPolicies.single().queryId)
                assertNotNull(info2.responseCode)
                assertTrue(info2.responseCode!!.isNotBlank())
            }

            test("Emit successful verification callback events") {
                webhook.assertReceivedInOrder(
                    sessionId,
                    Verifier2WebhookRecorder.successfulPresentationEvents,
                )
                webhook.assertDoesNotContain(sessionId, SessionEvent.presentation_validation_failed)
                webhook.assertDoesNotContain(sessionId, SessionEvent.dcql_fulfillment_check_failed)
                webhook.assertDoesNotContain(sessionId, SessionEvent.wallet_error_response_received)
            }
        }
        }
    }

    private fun Instant.wasWithinLastSeconds(): Boolean {
        val now = Clock.System.now()
        return this <= now && (now - this) <= 1500.milliseconds
    }
}
