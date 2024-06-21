import E2ETestWebService.test
import E2ETestWebService.testBlock
import id.walt.commons.config.ConfigManager
import id.walt.commons.web.plugins.httpJson
import id.walt.crypto.keys.KeyType
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.verifier.oidc.PresentationSessionInfo
import id.walt.webwallet.config.RegistrationDefaultsConfig
import id.walt.webwallet.db.models.*
import id.walt.webwallet.service.keys.SingleKeyResponse
import id.walt.webwallet.web.controllers.UsePresentationRequest
import id.walt.webwallet.web.model.EmailAccountRequest
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.util.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlinx.uuid.UUID
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class E2ETest {

    @Test
    fun e2e() = runTest(timeout = 5.minutes) {
        testBlock {
            var client = testHttpClient()

            // the e2e http request tests here
            //region -Login-
            test("/wallet-api/auth/user-info - not logged in without token") {
                client.get("/wallet-api/auth/user-info").apply {
                    assert(status == HttpStatusCode.Unauthorized) { "Was authorized without authorizing!" }
                }
            }

            test("/wallet-api/auth/login - wallet-api login") {
                client.post("/wallet-api/auth/login") {
                    setBody(EmailAccountRequest(email = "user@email.com", password = "password").encodeWithType("email"))
                }.expectSuccess().apply {
                    body<JsonObject>().let { result ->
                        assertNotNull(result["token"])
                        val token = result["token"]!!.jsonPrimitive.content.expectLooksLikeJwt()

                        client = testHttpClient(token = token)
                    }
                }
            }

            lateinit var accountId: UUID

            test("/wallet-api/auth/user-info - logged in after login") {
                client.get("/wallet-api/auth/user-info").expectSuccess().apply {
                    body<Account>().let { account ->
                        accountId = account.id
                    }
                }
            }

            test("/wallet-api/auth/session - logged in after login") {
                client.get("/wallet-api/auth/session").expectSuccess()
            }

            lateinit var wallet: UUID

            test("/wallet-api/wallet/accounts/wallets - get wallets") {
                client.get("/wallet-api/wallet/accounts/wallets").expectSuccess().apply {
                    val listing = body<AccountWalletListing>()
                    assert(listing.account == accountId) { "Wallet listing is for wrong account!" }

                    assert(listing.wallets.isNotEmpty()) { "No wallets available!" }
                    wallet = listing.wallets.first().id
                    println("Selected wallet: $wallet")
                }
            }
            //endregion -Login-

            //region -Keys-
            val defaultKeyConfig = ConfigManager.getConfig<RegistrationDefaultsConfig>().defaultKeyConfig
            val generateKeyType = KeyType.Ed25519
            val generateKeyBackend = "jwk"
            lateinit var generatedKeyId: String
            test("/wallet-api/wallet/{wallet}/keys - get keys") {
                client.get("/wallet-api/wallet/$wallet/keys").expectSuccess().apply {
                    val listing = body<List<SingleKeyResponse>>()
                    assert(listing.isNotEmpty()) { "No default key was created!" }
                    assert(KeyType.valueOf(listing[0].algorithm) == defaultKeyConfig.keyType) { "Default key type not ${defaultKeyConfig.keyType}" }
                }
            }
            test("/wallet-api/wallet/{wallet}/keys/generate - generate key") {
                client.post("/wallet-api/wallet/$wallet/keys/generate"){
                    setBody(
                        """
                        {
                          "backend": "$generateKeyBackend",
                          "keyType": "${generateKeyType.name}"
                        }
                    """.trimIndent()
                    )
                }.expectSuccess().apply {
                    generatedKeyId = body<String>()
                    assert(generatedKeyId.isNotEmpty()) { "Empty key id is returned!" }
                }
            }
            test("/wallet-api/wallet/{wallet}/keys/{keyId}/load - load key") {
                client.get("/wallet-api/wallet/$wallet/keys/$generatedKeyId/load").expectSuccess().apply {
                    val response = body<JsonElement>()
                    assertKeyComponents(response, generatedKeyId, generateKeyType, true)
                }
            }
            test("/wallet-api/wallet/{wallet}/keys/{keyId}/meta - key meta") {
                client.get("/wallet-api/wallet/$wallet/keys/$generatedKeyId/meta").expectSuccess().apply {
                    val response = body<JsonElement>()
                    assertNotNull(response.tryGetData("keyId")?.jsonPrimitive?.content) { "Missing _keyId_ component!" }
                    assert(response.tryGetData("keyId")?.jsonPrimitive?.content == generatedKeyId) { "Wrong _keyId_ value!" }
                    assertNotNull(response.tryGetData("type")?.jsonPrimitive?.content) { "Missing _type_ component!" }
                    when (generateKeyBackend) {
                        "jwt" -> assert(response.tryGetData("type")!!.jsonPrimitive.content.endsWith("JwkKeyMeta")) { "Missing _type_ component!" }
                        "tse" -> TODO()
                        "oci" -> TODO()
                        "oci-rest-api" -> TODO()
                        else -> Unit
                    }
                }
            }
            val keyExportFormat = "JWK"
            var keyExportPrivate = true
            test("/wallet-api/wallet/{wallet}/keys/{keyId}/export - export key") {
                client.get("/wallet-api/wallet/$wallet/keys/$generatedKeyId/export") {
                    url {
                        parameters.append("format", keyExportFormat)
                        parameters.append("loadPrivateKey", "$keyExportPrivate")
                    }
                }.expectSuccess().apply {
                    val response = Json.decodeFromString<JsonElement>(body<String>())
                    assertKeyComponents(response, generatedKeyId, generateKeyType, keyExportPrivate)
                }
            }
            test("/wallet-api/wallet/{wallet}/keys/{keyId}/meta - delete key") {
                client.delete("/wallet-api/wallet/$wallet/keys/$generatedKeyId").expectSuccess()
            }
            //endregion -Keys-

            lateinit var did: String

            test("/wallet-api/wallet/{wallet}/dids - list DIDs") {
                client.get("/wallet-api/wallet/$wallet/dids").expectSuccess().apply {
                    val dids = body<List<WalletDid>>()
                    assert(dids.isNotEmpty()) { "Wallet has no DIDs!" }

                    assert(dids.size == 1) { "Wallet has invalid number of DIDs!" }

                    did = dids.first().did
                    println("Selected DID: $did")
                }
            }

            test("/wallet-api/wallet/{wallet}/dids/{did} - show specific DID") {
                client.get("/wallet-api/wallet/$wallet/dids/$did").expectSuccess().apply {
                    val response = body<JsonObject>()
                    println("DID document: $response")
                }
            }

            test("/wallet-api/wallet/{wallet}/credentials - list credentials") {
                client.get("/wallet-api/wallet/$wallet/credentials").expectSuccess().apply {
                    val credentials = body<List<JsonObject>>()
                    assert(credentials.isEmpty()) { "should not have any credentials yet" }
                }
            }

            lateinit var offerUrl: String
            test("/openid4vc/jwt/issue - issue credential") {
                client.post("/openid4vc/jwt/issue") {
                    //language=JSON
                    setBody(
                        """
                    {
                      "issuerKey": {
                        "type": "jwk",
                        "jwk": {
                          "kty": "OKP",
                          "d": "mDhpwaH6JYSrD2Bq7Cs-pzmsjlLj4EOhxyI-9DM1mFI",
                          "crv": "Ed25519",
                          "kid": "Vzx7l5fh56F3Pf9aR3DECU5BwfrY6ZJe05aiWYWzan8",
                          "x": "T3T4-u1Xz3vAV2JwPNxWfs4pik_JLiArz_WTCvrCFUM"
                        }
                      },
                      "issuerDid": "did:key:z6MkjoRhq1jSNJdLiruSXrFFxagqrztZaXHqHGUTKJbcNywp",
                      "credentialConfigurationId": "OpenBadgeCredential_jwt_vc_json",
                      "credentialData": {
                        "@context": [
                          "https://www.w3.org/2018/credentials/v1",
                          "https://purl.imsglobal.org/spec/ob/v3p0/context.json"
                        ],
                        "id": "urn:uuid:THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION (see below)",
                        "type": [
                          "VerifiableCredential",
                          "OpenBadgeCredential"
                        ],
                        "name": "JFF x vc-edu PlugFest 3 Interoperability",
                        "issuer": {
                          "type": [
                            "Profile"
                          ],
                          "id": "did:key:THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION FROM CONTEXT (see below)",
                          "name": "Jobs for the Future (JFF)",
                          "url": "https://www.jff.org/",
                          "image": "https://w3c-ccg.github.io/vc-ed/plugfest-1-2022/images/JFF_LogoLockup.png"
                        },
                        "issuanceDate": "2023-07-20T07:05:44Z (THIS WILL BE REPLACED BY DYNAMIC DATA FUNCTION (see below))",
                        "expirationDate": "WILL BE MAPPED BY DYNAMIC DATA FUNCTION (see below)",
                        "credentialSubject": {
                          "id": "did:key:123 (THIS WILL BE REPLACED BY DYNAMIC DATA FUNCTION (see below))",
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
                          }
                        }
                      },
                      "mapping": {
                        "id": "<uuid>",
                        "issuer": {
                          "id": "<issuerDid>"
                        },
                        "credentialSubject": {
                          "id": "<subjectDid>"
                        },
                        "issuanceDate": "<timestamp>",
                        "expirationDate": "<timestamp-in:365d>"
                      }
                    }
                    """.trimIndent()
                    )
                }.expectSuccess().apply {
                    offerUrl = body<String>()
                    println("offer: $offerUrl")
                }
            }

            test("/wallet-api/wallet/{wallet}/exchange/resolveCredentialOffer - resolve credential offer") {
                client.post("/wallet-api/wallet/$wallet/exchange/resolveCredentialOffer") {
                    setBody(offerUrl)
                }.expectSuccess()
            }

            lateinit var newCredentialId: String
            test("/wallet-api/wallet/{wallet}/exchange/useOfferRequest - claim credential from issuer") {
                client.post("/wallet-api/wallet/$wallet/exchange/useOfferRequest") {
                    setBody(offerUrl)
                }.expectSuccess().run {
                    val newCredentials = body<List<WalletCredential>>()
                    assert(newCredentials.size == 1) { "should have received a credential" }

                    val cred = newCredentials.first()
                    newCredentialId = cred.id
                    newCredentialId
                }
            }

            test("/wallet-api/wallet/{wallet}/credentials - list credentials after issuance") {
                client.get("/wallet-api/wallet/$wallet/credentials").expectSuccess().apply {
                    val credentials = body<List<WalletCredential>>()
                    assert(credentials.size == 1) { "should have exactly 1 credential by now" }

                    assert(credentials.first().id == newCredentialId) { "credential should be the one received" }
                    credentials.map { it.id }
                }
            }

            lateinit var verificationUrl: String
            lateinit var verificationId: String
            test("/openid4vc/verify") {
                client.post("/openid4vc/verify") {
                    //language=JSON
                    setBody(
                        """
                        {
                          "request_credentials": [
                            "OpenBadgeCredential"
                          ]
                        }
                    """.trimIndent()
                    )
                }.expectSuccess().apply {
                    verificationUrl = body<String>()
                }
                assert(verificationUrl.contains("presentation_definition_uri="))
                assert(!verificationUrl.contains("presentation_definition="))

                verificationId = Url(verificationUrl).parameters.getOrFail("state")

                verificationUrl
            }

            lateinit var resolvedPresentationOfferString: String
            lateinit var presentationDefinition: String
            test("/wallet-api/wallet/{wallet}/exchange/resolvePresentationRequest - get presentation definition") {
                client.post("/wallet-api/wallet/$wallet/exchange/resolvePresentationRequest") {
                    contentType(ContentType.Text.Plain)
                    setBody(verificationUrl)
                }.expectSuccess().apply {
                    resolvedPresentationOfferString = body<String>()
                }
                assert(resolvedPresentationOfferString.contains("presentation_definition="))

                presentationDefinition = Url(resolvedPresentationOfferString).parameters.getOrFail("presentation_definition")

                presentationDefinition
            }

            test("/openid4vc/session/{id} - check if presentation definitions match") {
                client.get("/openid4vc/session/$verificationId").expectSuccess().apply {
                    val info = body<PresentationSessionInfo>()

                    assert(info.presentationDefinition == PresentationDefinition.fromJSONString(presentationDefinition))
                }
            }

            test("/wallet-api/wallet/{wallet}/exchange/matchCredentialsForPresentationDefinition - should match OpenBadgeCredential in wallet") {
                client.post("/wallet-api/wallet/$wallet/exchange/matchCredentialsForPresentationDefinition") {
                    setBody(presentationDefinition)
                }.expectSuccess().run {
                    val matched = body<List<WalletCredential>>()
                    assert(matched.size == 1) { "presentation definition should match 1 credential" }
                    assert(newCredentialId == matched.first().id) { "matched credential should be the received one" }
                    matched.map { it.id }
                }
            }

            test("/wallet-api/wallet/{wallet}/exchange/unmatchedCredentialsForPresentationDefinition - none should be missing") {
                client.post("/wallet-api/wallet/$wallet/exchange/unmatchedCredentialsForPresentationDefinition") {
                    setBody(presentationDefinition)
                }.expectSuccess().run {
                    val unmatched = body<List<WalletCredential>>()
                    assert(unmatched.isEmpty()) { "should not have not matched credentials (all 1 credential should match)" }
                    unmatched
                }
            }

            test("/wallet-api/wallet/{wallet}/exchange/usePresentationRequest - present credentials") {
                client.post("/wallet-api/wallet/$wallet/exchange/usePresentationRequest") {
                    setBody(
                        UsePresentationRequest(
                            did = did,
                            presentationRequest = resolvedPresentationOfferString,
                            selectedCredentials = listOf(newCredentialId)
                        )
                    )
                }.expectSuccess()
            }

            test("/openid4vc/session/{id} - check if presentation definitions match") {
                client.get("/openid4vc/session/$verificationId").expectSuccess().apply {
                    val info = body<PresentationSessionInfo>()

                    assert(info.tokenResponse?.vpToken?.jsonPrimitive?.contentOrNull?.expectLooksLikeJwt() != null) { "Received no valid token response!" }
                    assert(info.tokenResponse?.presentationSubmission != null) { "should have a presentation submission after submission" }

                    assert(info.verificationResult == true) { "overall verification should be valid" }
                    info.policyResults.let {
                        require(it != null) { "policyResults should be available after running policies" }
                        assert(it.size > 1) { "no policies have run" }
                    }
                }
            }

            test("/wallet-api/wallet/{wallet}/history - get operation history") {
                client.get("/wallet-api/wallet/$wallet/history").expectSuccess().apply {
                    val history = body<List<WalletOperationHistory>>()
                    assert(history.size >= 2) { "missing history items" }
                    assert(history.any { it.operation == "useOfferRequest" } && history.any { it.operation == "usePresentationRequest" }) { "incorrect history items" }
                }
            }
        }
    }

    fun testHttpClient(token: String? = null) = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(httpJson)
        }
        install(DefaultRequest) {
            contentType(ContentType.Application.Json)
            host = "127.0.0.1"
            port = 22222

            if (token != null) bearerAuth(token)
        }
        install(Logging) {
            level = LogLevel.ALL
        }
    }

    private fun String.expectLooksLikeJwt(): String =
        also { assert(startsWith("ey") && count { it == '.' } == 2) { "Does not look like JWT" } }

    private fun HttpResponse.expectSuccess(): HttpResponse = also {
        assert(status.isSuccess()) { "HTTP status is non-successful" }
    }

    @Test
    fun livez() = runTest {
        testBlock {
            val client = testHttpClient()
            test("/livez - healthcheck") {
                client.get("/livez").apply {
                    assert(status == HttpStatusCode.OK)
                    body<JsonElement>().let { result ->
                        assertTrue(result is JsonArray)
                        assertTrue(result.size>0)
                        assertTrue(result.tryGetData("status")?.jsonPrimitive?.content == "Healthy")
                    }
                }
            }
        }
    }

}

fun JsonElement.tryGetData(key: String): JsonElement? = key.split('.').let {
    var element: JsonElement? = this
    for (i in it) {
        element = when (element) {
            is JsonObject -> element[i]
            is JsonArray -> element.firstOrNull {
                it.jsonObject.containsKey(i)
            }?.let {
                it.jsonObject[i]
            }

            else -> element?.jsonPrimitive
        }
    }
    element
}

fun main(){
    val json = """
        [
            {
                "name": "http",
                "status": "Healthy",
                "lastCheck": "2024-06-20T20:18:19.877047821Z",
                "message": "ktor ready",
                "cause": null,
                "consecutiveSuccesses": 30789,
                "consecutiveFailures": 0
            }
        ]
    """.trimIndent()

    println(Json.decodeFromString<JsonElement>(json))
}