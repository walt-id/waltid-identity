package id.walt.verifier2.mdocs

import id.walt.commons.config.ConfigManager
import id.walt.commons.testing.E2ETest
import id.walt.dcql.DcqlMatcher
import id.walt.dcql.RawDcqlCredential
import id.walt.dcql.models.ClaimsQuery
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.meta.MsoMdocMeta
import id.walt.did.dids.DidService
import id.walt.did.dids.resolver.LocalResolver
import id.walt.ktornotifications.core.KtorSessionNotifications
import id.walt.policies2.vc.VCPolicyList
import id.walt.policies2.vc.policies.CredentialSignaturePolicy
import id.walt.policies2.vc.policies.RegexPolicy
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.verifier.openid.transactiondata.TransactionDataTypeRegistry
import id.walt.verifier2.OSSVerifier2FeatureCatalog
import id.walt.verifier2.OSSVerifier2ServiceConfig
import id.walt.verifier2.data.*
import id.walt.verifier2.events.Verifier2WebhookRecorder
import id.walt.verifier2.freePort
import id.walt.verifier2.handlers.sessioncreation.VerificationSessionCreationResponse
import id.walt.verifier2.verifierModule
import id.waltid.openid4vp.wallet.WalletPresentFunctionality2
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.server.application.*
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant


class MsoMdocsVerifier2IntegrationTest {

    private val mdocsDcqlQuery = DcqlQuery(
        credentials = listOf(
            CredentialQuery(
                id = "my_photoid",
                format = CredentialFormat.MSO_MDOC,
                meta = MsoMdocMeta(
                    doctypeValue = "org.iso.23220.photoid.1"
                ),
                claims = listOf(
                    ClaimsQuery(pathStrings = listOf("org.iso.18013.5.1", "family_name_unicode")),
                    ClaimsQuery(pathStrings = listOf("org.iso.18013.5.1", "given_name_unicode")),
                    ClaimsQuery(pathStrings = listOf("org.iso.18013.5.1", "issuing_authority_unicode")),
                    ClaimsQuery(
                        pathStrings = listOf("org.iso.18013.5.1", "resident_postal_code"),
                        values = listOf(1180, 1190, 1200, 1210).map { JsonPrimitive(it) }
                    ),
                    ClaimsQuery(
                        pathStrings = listOf("org.iso.18013.5.1", "issuing_country"),
                        values = listOf(JsonPrimitive("AT"))
                    ),
                    ClaimsQuery(pathStrings = listOf("org.iso.23220.photoid.1", "person_id")),
                    ClaimsQuery(pathStrings = listOf("org.iso.23220.photoid.1", "resident_street")),
                    ClaimsQuery(pathStrings = listOf("org.iso.23220.photoid.1", "administrative_number")),
                    ClaimsQuery(pathStrings = listOf("org.iso.23220.photoid.1", "travel_document_number")),

                    ClaimsQuery(pathStrings = listOf("org.iso.23220.dtc.1", "dtc_version")),
                    ClaimsQuery(pathStrings = listOf("org.iso.23220.dtc.1", "dtc_dg1"))
                )
            )
        )
    )

    private val mdocsPolicies = Verification2Session.DefinedVerificationPolicies(
        vc_policies = VCPolicyList(
            listOf(
                CredentialSignaturePolicy(),
                RegexPolicy(path = "$.['org.iso.23220.dtc.1'].dtc_version", regex = """^("[0-9]+"|-?[0-9]+(\.[0-9]+)?)$""")
            )
        )
    )

    private fun verificationSessionSetup(notifications: KtorSessionNotifications): VerificationSessionSetup =
        CrossDeviceFlowSetup(
            core = GeneralFlowConfig(
                dcqlQuery = mdocsDcqlQuery,
                policies = mdocsPolicies,
                notifications = notifications,
            )
        )

    private val walletCredentials = listOf(
        mdlFixtureCredential(
            """
            {
                "org.iso.18013.5.1": {
                    "family_name": "Doe",
                    "given_name": "John",
                    "birth_date": "1986-03-22",
                    "issue_date": "2019-10-20",
                    "expiry_date": "2024-10-20",
                    "issuing_country": "AT",
                    "issuing_authority": "AT DMV",
                    "document_number": 123456789,
                    "un_distinguishing_sign": "AT"
                },
                "docType": "org.iso.18013.5.1.mDL"
            }
            """
        ),
        mdlFixtureCredential(
            """
            {
                "org.iso.18013.5.1": {
                    "family_name_unicode": "Doe",
                    "given_name_unicode": "John",
                    "birth_date": "2000-01-20",
                    "issue_date": "2025-09-12",
                    "expiry_date": "2030-09-12",
                    "issuing_authority_unicode": "LPD Wien 22",
                    "issuing_country": "AT",
                    "sex": 1,
                    "nationality": "AT",
                    "document_number": 1234567890,
                    "name_at_birth": "Max Mustermann",
                    "birthplace": "Baden bei Wien",
                    "resident_address_unicode": "Püchlgasse 1D, 4.4.4.",
                    "resident_city_unicode": "Vienna",
                    "resident_postal_code": 1190,
                    "resident_country": "AT",
                    "age_over_18": true,
                    "age_in_years": 25,
                    "age_birth_year": 2000,
                    "family_name_latin1": "Mustermann",
                    "given_name_latin1": "Max"
                },
                "org.iso.23220.photoid.1": {
                    "person_id": "AT12345",
                    "birth_country": "AT",
                    "birth_state": "LOWER AUSTRIA",
                    "birth_city": "Baden bei Wien",
                    "administrative_number": "ATATAT123",
                    "resident_street": "Püchlgasse",
                    "resident_house_number": "1D, 4.4.4.",
                    "travel_document_number": 1122334,
                    "resident_state": "VIENNA"
                },
                "org.iso.23220.dtc.1": {
                    "dtc_version": 1,
                    "dtc_dg1": "P<AUTDOE<<JOHN<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<0011223340AUT2001207M3009129<<<<<<<<<<<<<<06"
                },
                "docType": "org.iso.23220.photoid.1"
            }
            """
        )
    )

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
                // walletCredentials only contains MdocsCredential in this test; no SD disclosures
                disclosures = null
            )
        }

        val matched = DcqlMatcher.match(query, dcqlCredentials).getOrThrow()
        if (matched.isEmpty()) {
            throw IllegalArgumentException("No matching credential")
        }

        return matched
    }

    @Test
    fun test() = runVerifierWalletFlow(clientId = null)

    @Test
    fun `omitted clientId generates redirect_uri and wallet presents`() =
        runVerifierWalletFlow(clientId = null)

    private fun runVerifierWalletFlow(clientId: String?) {
        val host = "127.0.0.1"
        val port = freePort()
        Verifier2WebhookRecorder().start().use { webhook ->
        E2ETest(host, port, true).testBlock(
            features = listOf(OSSVerifier2FeatureCatalog),
            preload = {
                ConfigManager.preloadConfig(
                    "verifier-service", OSSVerifier2ServiceConfig(
                        clientId = clientId,
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
                    setBody(verificationSessionSetup(webhook.notifications()))
                }.body<VerificationSessionCreationResponse>()
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

            if (clientId == null) {
                val responseUri = "http://$host:$port/verification-session/$sessionId/response"
                test("Omitted clientId is generated as redirect_uri bound to response_uri") {
                    assertEquals(responseUri, info1.authorizationRequest.responseUri)
                    assertEquals("redirect_uri:$responseUri", info1.authorizationRequest.clientId)
                    assertEquals("redirect_uri:$responseUri", info1.bootstrapAuthorizationRequest?.clientId)
                }
            }

            // Present with wallet
            val bootstrapUrl = verificationSessionResponse.bootstrapAuthorizationRequestUrl

            val holderKey = restoreMdlHolderCrypto2Key("cross-device-mdoc-holder")

            val selectCallback: suspend (DcqlQuery) -> Map<String, List<DcqlMatcher.DcqlMatchResult>> = { query ->
                selectCredentialsForQuery(
                    query = query
                )
            }

            val presentationResult = testAndReturn("Present with wallet") {
                WalletPresentFunctionality2.walletPresentHandling(
                    holderKey = holderKey,
                    holderDid = null, // No DID required for mso_mdoc
                    presentationRequestUrl = bootstrapUrl!!,
                    selectCredentialsForQuery = selectCallback,
                    holderPoliciesToRun = null,
                    runPolicies = null,
                    transactionDataTypeRegistry = TransactionDataTypeRegistry(emptySet()),
                    mdocHolderKeyResolver = { _, _ -> holderKey },
                )
            }

            println("Presentation result: $presentationResult")

            // Check presentation result by wallet
            test("Verify presentation result") {
                assertTrue { presentationResult.isSuccess }

                val resp = presentationResult.getOrThrow()
                assertTrue("Transmission success is false") { resp.transmissionSuccess == true }
                assertTrue { resp.verifierResponse!!.jsonObject["status"]!!.jsonPrimitive.content == "received" }
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
                assertNotNull(info2.presentedCredentials!!["my_photoid"])
                assertTrue { info2.presentedCredentials!!["my_photoid"]!!.size == 1 }

                assertNotNull(info2.policyResults)
                assertTrue { info2.policyResults!!.overallSuccess }
                assertTrue { info2.policyResults!!.vcPolicies.size == 2 }
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

    // Utils:
    private fun Instant.wasWithinLastSeconds(): Boolean {
        val now = Clock.System.now()
        return this <= now && (now - this) <= 1500.milliseconds
    }
}
