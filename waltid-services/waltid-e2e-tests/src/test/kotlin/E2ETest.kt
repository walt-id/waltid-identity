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
import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyType
import id.walt.issuer.issuance.IssuanceRequest
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.webwallet.db.models.WalletCredential
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

    @Test
    fun e2e() = testBlock(defaultTestTimeout) {
        var client = testHttpClient()
        lateinit var wallet: UUID

        // E2E tests here:

        testGroup("Authentication") {
            var authApi = AuthApi(client)
            with(authApi) {
                test("Should be unauthorized when not logged in") {
                    userInfo(HttpStatusCode.Unauthorized) ?: "Unauthorized -> no account"
                }
                test("Login") {
                    val token = login(defaultEmailAccount)
                    client = testHttpClient(token)
                    authApi = AuthApi(client)
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
            val keysApi = KeysApi(client, wallet)
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
            val didsApi = DidsApi(client, wallet)

            with(didsApi) {
                list(DidsApi.DefaultDidOption.Any, 1) {
                    assert(it.first().default)
                    did = it.first().did
                }
                val createdDids = listOf(
                    create(DidsApi.DidCreateRequest(method = "key", options = mapOf("useJwkJcsPub" to false))),
                    create(DidsApi.DidCreateRequest(method = "jwk")),
                    create(DidsApi.DidCreateRequest(method = "web", options = mapOf("domain" to "domain", "path" to "path")))
                )

                setDefault(createdDids[0])
                list(DidsApi.DefaultDidOption.Some(createdDids[0]), createdDids.size + 1)
                createdDids.forEach { did ->
                    delete(did)
                }
                list(DidsApi.DefaultDidOption.None, 1)
                get(did)
                setDefault(did)
                list(DidsApi.DefaultDidOption.Some(did), 1)
            }
        }


        lateinit var offerUrl: String
        testGroup("Issuer / offer URL") {
            val issuerApi = IssuerApi(client)
            val issuanceRequest = Json.decodeFromJsonElement<IssuanceRequest>(jwtCredential)
            println("issuance-request: $issuanceRequest")
            issuerApi.jwt(issuanceRequest) {
                offerUrl = it
                println("offer: $offerUrl")
            }
        }

        val exchangeApi = ExchangeApi(client, wallet)
        lateinit var newCredentialId: String
        testGroup("Claim credential") {
            exchangeApi.resolveCredentialOffer(offerUrl)
            exchangeApi.useOfferRequest(offerUrl, 1) {
                val cred = it.first()
                newCredentialId = cred.id
            }
        }

        val credentialsApi = CredentialsApi(client, wallet)
        testGroup("Credentials") {
            with(credentialsApi) {
                list(expectedSize = 1, expectedCredential = arrayOf(newCredentialId))
                get(newCredentialId)
                accept(newCredentialId)
                delete(newCredentialId)
                restore(newCredentialId)
                status(newCredentialId)
                // reject(newCredentialId)
                // delete(newCredentialId, true)
            }
        }

        lateinit var verificationUrl: String
        lateinit var verificationId: String
        val sessionApi = Verifier.SessionApi(client)
        val verificationApi = Verifier.VerificationApi(client)
        testGroup("Verifier / request url") {
            verificationApi.verify(simplePresentationRequestPayload) {
                verificationUrl = it
                verificationId = Url(verificationUrl).parameters.getOrFail("state")
            }
        }

        testGroup("Exchange / presentation") {
            lateinit var resolvedPresentationOfferString: String
            lateinit var presentationDefinition: String
            with(exchangeApi) {
                resolvePresentationRequest(verificationUrl) {
                    resolvedPresentationOfferString = it
                    presentationDefinition = Url(it).parameters.getOrFail("presentation_definition")
                }

                sessionApi.get(verificationId) {
                    assert(it.presentationDefinition == PresentationDefinition.fromJSONString(presentationDefinition))
                }

                matchCredentialsForPresentationDefinition(presentationDefinition, listOf(newCredentialId))
                unmatchedCredentialsForPresentationDefinition(presentationDefinition)
                usePresentationRequest(UsePresentationRequest(did, resolvedPresentationOfferString, listOf(newCredentialId)))

                sessionApi.get(verificationId) {
                    assert(it.tokenResponse?.vpToken?.jsonPrimitive?.contentOrNull?.expectLooksLikeJwt() != null) { "Received no valid token response!" }
                    assert(it.tokenResponse?.presentationSubmission != null) { "should have a presentation submission after submission" }

                    assert(it.verificationResult == true) { "overall verification should be valid" }
                    it.policyResults.let {
                        require(it != null) { "policyResults should be available after running policies" }
                        assert(it.size > 1) { "no policies have run" }
                    }
                }
            }
        }

        val categoryApi = CategoryApi(client, wallet)
        testGroup("Categories") {
            val categoryName = "name#1"
            val categoryNewName = "name#2"
            with(categoryApi) {
                list(0)
                add(categoryName)
                assertNotNull(list(1).single { it["name"].asString() == categoryName })
                rename(categoryName, categoryNewName)
                assertNotNull(list(1).single { it["name"].asString() == categoryNewName })
                delete(categoryNewName)

                add(categoryName)
                add(categoryNewName)
            }

            with(credentialsApi) {
                attachCategory(newCredentialId, categoryName, categoryNewName)
                detachCategory(newCredentialId, categoryName, categoryNewName)
            }
        }

        testGroup("History") {
            val historyApi = HistoryApi(client)
            historyApi.list(wallet) {
                assert(it.size >= 2) { "missing history items" }
                assert(it.any { it.operation == "useOfferRequest" } && it.any { it.operation == "usePresentationRequest" }) { "incorrect history items" }
            }
        }

        test("Clear up credential") {
            credentialsApi.delete(newCredentialId, true)
        }

        testGroup("SD-JWT") {
            // todo: make this cleaner:

            //region -Issuer / offer url-
            lateinit var offerUrl: String
            val issuerApi = IssuerApi(client)
            val issuanceRequest = Json.decodeFromJsonElement<IssuanceRequest>(sdjwtCredential)
            println("issuance-request:")
            println(issuanceRequest)
            issuerApi.sdjwt(issuanceRequest) {
                offerUrl = it
                println("offer: $offerUrl")
            }
            //endregion -Issuer / offer url-

            //region -Exchange / claim-
            val exchangeApi = ExchangeApi(client, wallet)
            lateinit var newCredential: WalletCredential
            exchangeApi.resolveCredentialOffer(offerUrl)
            exchangeApi.useOfferRequest(offerUrl, 1) {
                newCredential = it.first()
            }
            //endregion -Exchange / claim-

            //region -Verifier / request url-
            lateinit var verificationUrl: String
            lateinit var verificationId: String
            val sessionApi = Verifier.SessionApi(client)
            val verificationApi = Verifier.VerificationApi(client)
            verificationApi.verify(nameFieldSchemaPresentationRequestPayload) {
                verificationUrl = it
                verificationId = Url(verificationUrl).parameters.getOrFail("state")
            }
            //endregion -Verifier / request url-

            //region -Exchange / presentation-
            lateinit var resolvedPresentationOfferString: String
            lateinit var presentationDefinition: String
            exchangeApi.resolvePresentationRequest(verificationUrl) {
                resolvedPresentationOfferString = it
                presentationDefinition = Url(it).parameters.getOrFail("presentation_definition")
            }

            sessionApi.get(verificationId) {
                assert(it.presentationDefinition == PresentationDefinition.fromJSONString(presentationDefinition))
            }

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

            sessionApi.get(verificationId) {
                assert(it.tokenResponse?.vpToken?.jsonPrimitive?.contentOrNull?.expectLooksLikeJwt() != null) { "Received no valid token response!" }
                assert(it.tokenResponse?.presentationSubmission != null) { "should have a presentation submission after submission" }

                assert(it.verificationResult == false) { "overall verification should be valid" }
                it.policyResults.let {
                    require(it != null) { "policyResults should be available after running policies" }
                    assert(it.size > 1) { "no policies have run" }
                }
            }
            //endregion -Exchange / presentation-
        }
    }
}
