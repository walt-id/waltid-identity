import E2EResources.defaultEmailAccount
import E2EResources.defaultTestTimeout
import E2EResources.jwtCredential
import E2EResources.nameFieldSchemaPresentationRequestPayload
import E2EResources.sdjwtCredential
import E2EResources.simplePresentationRequestPayload
import E2ETestWebService.inlineTest
import E2ETestWebService.inlineTestWithResult
import E2ETestWebService.loadResource
import E2ETestWebService.test
import E2ETestWebService.testBlock
import E2ETestWebService.testGroup
import E2ETestWebService.testWithResult
import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyType
import id.walt.issuer.issuance.IssuanceRequest
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.webwallet.web.controllers.UsePresentationRequest
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.util.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.uuid.UUID
import kotlin.test.Test
import kotlin.test.assertNotNull

class E2ETest {

    var walletHttpPort: Int = 22222
    // var walletHttpPort: Int = 7001
    var issuerHttp: Int = 22222
    // var issuerHttp: Int = 7002
    var verifierHttp: Int = 22222
    // var verifierHttp: Int = 7003

    @Test
    fun e2e() = testBlock(defaultTestTimeout) {
        var walletHttp = testHttpClient(port = walletHttpPort)
        val issuerHttp = testHttpClient(port = issuerHttp)
        val verifierHttp = testHttpClient(port = verifierHttp)

        lateinit var wallet: UUID

        // E2E tests here:

        testGroup("Authentication") {
            var authApi = AuthApi(walletHttp)
            with(authApi) {
                test("Should be unauthorized when not logged in") {
                    userInfo(HttpStatusCode.Unauthorized) ?: "Unauthorized -> no account"
                }
                test("Login") {
                    val token = login(defaultEmailAccount)
                    walletHttp = testHttpClient(token, port = walletHttpPort)
                    authApi = AuthApi(walletHttp)
                    token
                }
            }
            with(authApi) {
                test("Endpoints should be usable when logged in") {
                    val account = userInfo(HttpStatusCode.OK)!!
                    userSession()
                    val wallets = userWallets(account.id)
                    wallet = wallets.first().id
                    println("Selected wallet: $wallet")
                }
            }
        }

        testGroup("Keys") {
            val keysApi = KeysApi(walletHttp, wallet)
            with(keysApi) {
                "Test key listing" inlineTest { keysApi.list() }
                val keyGenRequest = KeyGenerationRequest("jwk", KeyType.Ed25519)
                val generatedKeyId = "Test key generation" inlineTestWithResult { keysApi.generate(keyGenRequest) }
                "Test key loading" inlineTest { keysApi.load(generatedKeyId, keyGenRequest) }
                "Test key metadata" inlineTest { keysApi.meta(generatedKeyId, keyGenRequest) }
                "Test key exporting" inlineTest { keysApi.export(generatedKeyId, "JWK", true, keyGenRequest) }
                "Test key deletion" inlineTest { keysApi.delete(generatedKeyId) }
                val rsaJwkImport = loadResource("keys/rsa.json")
                "Test key import (RSA)" inlineTest { keysApi.import(rsaJwkImport) }
            }
        }

        lateinit var did: String
        testGroup("DIDs") {
            val didsApi = DidsApi(walletHttp, wallet)

            with(didsApi) {
                test("Has default DID") {
                    val firstDid = list(DidsApi.DefaultDidOption.Any, 1).first()
                    assert(firstDid.default)
                    did = firstDid.did
                    firstDid
                }

                val createdDids = listOf(
                    DidsApi.DidCreateRequest(method = "key", options = mapOf("useJwkJcsPub" to false)),
                    DidsApi.DidCreateRequest(method = "jwk"),
                    DidsApi.DidCreateRequest(method = "web", options = mapOf("domain" to "domain", "path" to "path"))
                ).map {
                    testWithResult("Create did:${it.method}") { create(it) }
                }

                test("Set default DID") {
                    setDefault(createdDids[0])
                    list(DidsApi.DefaultDidOption.Some(createdDids[0]), createdDids.size + 1)
                }

                test("Delete all created DIDs") {
                    createdDids.forEach { did -> delete(did) }
                    list(DidsApi.DefaultDidOption.None, 1)
                }

                test("Set default DID (again)") {
                    get(did)
                    setDefault(did)
                    list(DidsApi.DefaultDidOption.Some(did), 1)
                }
            }
        }


        lateinit var offerUrl: String
        testGroup("Issuer / offer URL") {
            val issuerApi = IssuerApi(issuerHttp)
            val issuanceRequest = Json.decodeFromJsonElement<IssuanceRequest>(jwtCredential)
            println("issuance-request: $issuanceRequest")

            "Issue JWT" inlineTest { offerUrl = issuerApi.jwt(issuanceRequest); offerUrl }
        }

        val exchangeApi = ExchangeApi(walletHttp, wallet)
        lateinit var newCredentialId: String
        testGroup("Claim credential") {
            "Resolve credential offer" inlineTest { exchangeApi.resolveCredentialOffer(offerUrl) }
            "Use offer request" inlineTest { newCredentialId = exchangeApi.useOfferRequest(offerUrl, 1).first().id; newCredentialId }

            newCredentialId
        }

        val credentialsApi = CredentialsApi(walletHttp, wallet)
        testGroup("Credentials") {
            with(credentialsApi) {
                "List credentials" inlineTest { list(expectedSize = 1, expectedCredential = arrayOf(newCredentialId)) }
                "View new credential" inlineTest { get(newCredentialId) }
                "Accept the new credential" inlineTest { accept(newCredentialId) }
                "(Temp-)Delete the new credential again " inlineTest { delete(newCredentialId) }
                "Restore the deleted credential" inlineTest { restore(newCredentialId) }
                "View credential status" inlineTest { status(newCredentialId) }
                // reject(newCredentialId)
                // delete(newCredentialId, true)
            }
        }

        lateinit var verificationUrl: String
        lateinit var verificationId: String
        val sessionApi = Verifier.SessionApi(verifierHttp)
        val verificationApi = Verifier.VerificationApi(verifierHttp)
        testGroup("Verifier / request url") {
            "Start verification" inlineTest { verificationUrl = verificationApi.verify(simplePresentationRequestPayload); verificationUrl }
            verificationId = Url(verificationUrl).parameters.getOrFail("state")

            verificationUrl
        }

        testGroup("Exchange / presentation") {
            lateinit var resolvedPresentationOffer: String
            lateinit var presentationDefinition: String
            with(exchangeApi) {
                "Resolve " inlineTest { resolvedPresentationOffer = resolvePresentationRequest(verificationUrl); resolvedPresentationOffer }
                presentationDefinition = Url(resolvedPresentationOffer).parameters.getOrFail("presentation_definition")

                var presentationSession = sessionApi.get(verificationId)
                assert(presentationSession.presentationDefinition == PresentationDefinition.fromJSONString(presentationDefinition))

                matchCredentialsForPresentationDefinition(presentationDefinition, listOf(newCredentialId))
                unmatchedCredentialsForPresentationDefinition(presentationDefinition)

                test("Use presentation request") {
                    usePresentationRequest(UsePresentationRequest(did, resolvedPresentationOffer, listOf(newCredentialId)))
                }

                presentationSession = sessionApi.get(verificationId)
                assert(presentationSession.tokenResponse?.vpToken?.jsonPrimitive?.contentOrNull?.expectLooksLikeJwt() != null) { "Received no valid token response!" }
                assert(presentationSession.tokenResponse?.presentationSubmission != null) { "should have a presentation submission after submission" }

                assert(presentationSession.verificationResult == true) { "overall verification should be valid" }
                presentationSession.policyResults.let {
                    require(it != null) { "policyResults should be available after running policies" }
                    assert(it.size > 1) { "no policies have run" }
                }
            }
        }

        val categoryApi = CategoryApi(walletHttp, wallet)
        testGroup("Categories") {
            val categoryName = "name#1"
            val categoryNewName = "name#2"
            with(categoryApi) {
                "Check no categories by default" inlineTest { list(0) }

                test("Add category") {
                    add(categoryName)
                    assertNotNull(list(1).single { it["name"].asString() == categoryName })
                }

                test("Rename category") {
                    rename(categoryName, categoryNewName)
                    assertNotNull(list(1).single { it["name"].asString() == categoryNewName })
                }

                test("Delete category") {
                    delete(categoryNewName)
                    list(0)
                }

                test("Add other categories") {
                    add(categoryName)
                    add(categoryNewName)
                }
            }

            with(credentialsApi) {
                test("Attach categories to credentials") {
                    attachCategory(newCredentialId, categoryName, categoryNewName)
                    detachCategory(newCredentialId, categoryName, categoryNewName)
                }
            }
        }

        testGroup("History") {
            test("Test history") {
                HistoryApi(walletHttp).list(wallet).also { history ->
                    assert(history.size >= 2) { "missing history items" }
                    assert(history.any { it.operation == "useOfferRequest" } && history.any { it.operation == "usePresentationRequest" }) { "incorrect history items" }
                }.last()
            }
        }

        test("Clear up credential") {
            credentialsApi.delete(newCredentialId, true)
        }

        testGroup("SD-JWT") {
            // todo: make this cleaner:
            test("SD-JWT") {

                //region -Issuer / offer url-
                val issuerApi = IssuerApi(issuerHttp)
                val issuanceRequest = Json.decodeFromJsonElement<IssuanceRequest>(sdjwtCredential)
                println("issuance-request:")
                println(issuanceRequest)
                val offerUrl = issuerApi.sdjwt(issuanceRequest)
                //endregion -Issuer / offer url-

                //region -Exchange / claim-
                val exchangeApi = ExchangeApi(walletHttp, wallet)
                exchangeApi.resolveCredentialOffer(offerUrl)
                val newCredential = exchangeApi.useOfferRequest(offerUrl, 1).first()
                //endregion -Exchange / claim-

                //region -Verifier / request url-
                val sessionApi = Verifier.SessionApi(verifierHttp)
                val verificationApi = Verifier.VerificationApi(verifierHttp)
                val verificationUrl: String = verificationApi.verify(nameFieldSchemaPresentationRequestPayload)
                val verificationId: String = Url(verificationUrl).parameters.getOrFail("state")
                //endregion -Verifier / request url-

                //region -Exchange / presentation-
                val resolvedPresentationOfferString: String = exchangeApi.resolvePresentationRequest(verificationUrl)
                val presentationDefinition = Url(resolvedPresentationOfferString).parameters.getOrFail("presentation_definition")

                var presentationSession = sessionApi.get(verificationId)
                assert(presentationSession.presentationDefinition == PresentationDefinition.fromJSONString(presentationDefinition))

                exchangeApi.matchCredentialsForPresentationDefinition(presentationDefinition, listOf(newCredential.id))
                exchangeApi.unmatchedCredentialsForPresentationDefinition(presentationDefinition)
                exchangeApi.usePresentationRequest(
                    request = UsePresentationRequest(
                        did = did,
                        presentationRequest = resolvedPresentationOfferString,
                        selectedCredentials = listOf(newCredential.id),
                        disclosures = newCredential.disclosures?.let { mapOf(newCredential.id to listOf(it)) },
                    ),
                    expectStatus = HttpResponse::expectFailure,
                )

                presentationSession = sessionApi.get(verificationId)
                assert(presentationSession.tokenResponse?.vpToken?.jsonPrimitive?.contentOrNull?.expectLooksLikeJwt() != null) { "Received no valid token response!" }
                assert(presentationSession.tokenResponse?.presentationSubmission != null) { "should have a presentation submission after submission" }

                assert(presentationSession.verificationResult == false) { "overall verification should be valid" }
                presentationSession.policyResults.let {
                    require(it != null) { "policyResults should be available after running policies" }
                    assert(it.size > 1) { "no policies have run" }
                }
                //endregion -Exchange / presentation-
            }
        }
    }
}
