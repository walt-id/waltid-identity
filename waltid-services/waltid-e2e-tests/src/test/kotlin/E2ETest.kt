import E2ETestWebService.loadResource
import E2ETestWebService.testBlock
import id.walt.commons.config.ConfigManager
import id.walt.commons.web.plugins.httpJson
import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyType
import id.walt.issuer.issuance.IssuanceRequest
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.webwallet.config.RegistrationDefaultsConfig
import id.walt.webwallet.db.models.AccountWalletListing
import id.walt.webwallet.web.model.AccountRequest
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
import kotlin.time.Duration.Companion.minutes

class E2ETest {

    @Test
    fun e2e() = runTest(timeout = 5.minutes) {
        testBlock {
            var client = testHttpClient()
            lateinit var accountId: UUID
            lateinit var wallet: UUID
            var authApi = AuthApi(client)

            // the e2e http request tests here

            //region -Auth-
            authApi.userInfo(HttpStatusCode.Unauthorized)
            authApi.login(
                EmailAccountRequest(
                    email = "user@email.com",
                    password = "password"
                )
            ) {
                client = testHttpClient(token = it["token"]!!.jsonPrimitive.content)
                authApi = AuthApi(client)
            }
            authApi.userInfo(HttpStatusCode.OK) {
                accountId = it.id
            }
            authApi.userSession()
            authApi.userWallets(accountId) {
                wallet = it.wallets.first().id
                println("Selected wallet: $wallet")
            }
            //endregion -Auth-

            //region -Keys-
            val keysApi = KeysApi(client)
            val defaultKeyConfig = ConfigManager.getConfig<RegistrationDefaultsConfig>().defaultKeyConfig
            val keyGenRequest = KeyGenerationRequest("jwk", KeyType.Ed25519)
            lateinit var generatedKeyId: String
            val rsaJwkImport = loadResource("keys/rsa.json")
            keysApi.list(wallet, defaultKeyConfig)
            keysApi.generate(wallet, keyGenRequest) { generatedKeyId = it }
            keysApi.load(wallet, generatedKeyId, keyGenRequest)
            keysApi.meta(wallet, generatedKeyId, keyGenRequest)
            keysApi.export(wallet, generatedKeyId, "JWK", true, keyGenRequest)
            keysApi.delete(wallet, generatedKeyId)
            keysApi.import(wallet, rsaJwkImport)
            //endregion -Keys-

            //region -Dids-
            val didsApi = DidsApi(client)
            lateinit var did: String
            val createdDids = mutableListOf<String>()
            didsApi.list(wallet, 1, DidsApi.DefaultDidOption.Any) {
                assert(it.first().default)
                did = it.first().did
            }
            didsApi.create(wallet, DidsApi.DidCreateRequest(method = "key", options = mapOf("useJwkJcsPub" to false))) {
                createdDids.add(it)
            }
            didsApi.create(wallet, DidsApi.DidCreateRequest(method = "jwk")) {
                createdDids.add(it)
            }
            didsApi.create(
                wallet,
                DidsApi.DidCreateRequest(method = "web", options = mapOf("domain" to "domain", "path" to "path"))
            ) {
                createdDids.add(it)
            }
            /* Flaky test - sometimes works fine, sometimes responds with 400:
            didsApi.create(
                wallet, DidsApi.DidCreateRequest(method = "cheqd", options = mapOf("network" to "testnet"))
            ) {
                createdDids.add(it)
            }*/

            //TODO: error(400) DID method not supported for auto-configuration: ebsi
//            didsApi.create(wallet, DidsApi.DidCreateRequest(method = "ebsi", options = mapOf("version" to 2, "bearerToken" to "token"))){
//                createdDids.add(it)
//            }

            //TODO: didsApi.create(wallet, DidsApi.DidCreateRequest(method = "iota")){ createdDids.add(it) }
            didsApi.default(wallet, createdDids[0])
            didsApi.list(wallet, createdDids.size + 1, DidsApi.DefaultDidOption.Some(createdDids[0]))
            for (d in createdDids) {
                didsApi.delete(wallet, d)
            }
            didsApi.list(wallet, 1, DidsApi.DefaultDidOption.None)
            didsApi.get(wallet, did)
            didsApi.default(wallet, did)
            didsApi.list(wallet, 1, DidsApi.DefaultDidOption.Some(did))
            //endregion -Dids-

            //region -Categories-
            val categoryApi = CategoryApi(client)
            val categoryName = "name#1"
            val categoryNewName = "name#2"
            categoryApi.list(wallet, 0)
            categoryApi.add(wallet, categoryName)
            categoryApi.list(wallet, 1){
                assertNotNull(it.single { it["name"]?.jsonPrimitive?.content == categoryName })
            }
            categoryApi.rename(wallet, categoryName, categoryNewName)
            categoryApi.list(wallet, 1){
                assertNotNull(it.single { it["name"]?.jsonPrimitive?.content == categoryNewName })
            }
            categoryApi.delete(wallet, categoryNewName)
            //endregion -Categories

            //region -Issuer / offer url-
            lateinit var offerUrl: String
            val issuerApi = IssuerApi(client)
            val issuanceRequest =
                Json.decodeFromString<IssuanceRequest>(loadResource("issuance/openbadgecredential-issuance-request.json"))
            issuerApi.issue(issuanceRequest) {
                offerUrl = it
                println("offer: $offerUrl")
            }
            //endregion -Issuer / offer url-

            //region -Exchange / claim-
            val exchangeApi = ExchangeApi(client)
            lateinit var newCredentialId: String
            exchangeApi.resolveCredentialOffer(wallet, offerUrl)
            exchangeApi.useOfferRequest(wallet, offerUrl, 1) {
                val cred = it.first()
                newCredentialId = cred.id
            }
            //endregion -Exchange / claim-

            //region -Credentials-
            val credentialsApi = CredentialsApi(client)
            credentialsApi.list(wallet, expectedSize = 1, expectedCredential = arrayOf(newCredentialId))
            credentialsApi.get(wallet, newCredentialId)
            credentialsApi.accept(wallet, newCredentialId)
            credentialsApi.delete(wallet, newCredentialId)
            credentialsApi.restore(wallet, newCredentialId)
            credentialsApi.status(wallet, newCredentialId)
            categoryApi.add(wallet, categoryName)
            categoryApi.add(wallet, categoryNewName)
            credentialsApi.attachCategory(wallet, newCredentialId, categoryName, categoryNewName)
            credentialsApi.detachCategory(wallet, newCredentialId, categoryName, categoryNewName)
//            credentialsApi.reject(wallet, newCredentialId)
//            credentialsApi.delete(wallet, newCredentialId, true)
            //endregion -Credentials-

            //region -Verifier / request url-
            lateinit var verificationUrl: String
            lateinit var verificationId: String
            val sessionApi = Verifier.SessionApi(client)
            val verificationApi = Verifier.VerificationApi(client)
            verificationApi.verify(loadResource("presentation/openbadgecredential-presentation-request.json")) {
                verificationUrl = it
                assert(verificationUrl.contains("presentation_definition_uri="))
                assert(!verificationUrl.contains("presentation_definition="))
                verificationId = Url(verificationUrl).parameters.getOrFail("state")
                verificationUrl
            }
            //endregion -Verifier / request url-

            //region -Exchange / presentation-
            lateinit var resolvedPresentationOfferString: String
            lateinit var presentationDefinition: String
            exchangeApi.resolvePresentationRequest(wallet, verificationUrl) {
                resolvedPresentationOfferString = it
                presentationDefinition = Url(it).parameters.getOrFail("presentation_definition")
            }

            sessionApi.get(verificationId) {
                assert(it.presentationDefinition == PresentationDefinition.fromJSONString(presentationDefinition))
            }

            exchangeApi.matchCredentialsForPresentationDefinition(
                wallet, presentationDefinition, listOf(newCredentialId)
            )
            exchangeApi.unmatchedCredentialsForPresentationDefinition(wallet, presentationDefinition)
            exchangeApi.usePresentationRequest(wallet, did, resolvedPresentationOfferString, listOf(newCredentialId))

            sessionApi.get(verificationId){
                assert(it.tokenResponse?.vpToken?.jsonPrimitive?.contentOrNull?.expectLooksLikeJwt() != null) { "Received no valid token response!" }
                assert(it.tokenResponse?.presentationSubmission != null) { "should have a presentation submission after submission" }

                assert(it.verificationResult == true) { "overall verification should be valid" }
                it.policyResults.let {
                    require(it != null) { "policyResults should be available after running policies" }
                    assert(it.size > 1) { "no policies have run" }
                }
            }
            val lspPotentialIssuance = LspPotentialIssuance(testHttpClient(doFollowRedirects = false))
            lspPotentialIssuance.testTrack1()
            lspPotentialIssuance.testTrack2()
            val lspPotentialVerification = LspPotentialVerification(testHttpClient(doFollowRedirects = false))
            lspPotentialVerification.testPotentialInteropTrack3()
            lspPotentialVerification.testPotentialInteropTrack4()
            val lspPotentialWallet = setupTestWallet()
            lspPotentialWallet.testMDocIssuance()
            lspPotentialWallet.testMdocPresentation()
            lspPotentialWallet.testSDJwtVCIssuance()
            lspPotentialWallet.testSDJwtPresentation()

            //endregion -Exchange / presentation-

            //region -History-
            val historyApi = HistoryApi(client)
            historyApi.list(wallet) {
                assert(it.size >= 2) { "missing history items" }
                assert(it.any { it.operation == "useOfferRequest" } && it.any { it.operation == "usePresentationRequest" }) { "incorrect history items" }
            }
            //endregion -History-

            // Test Authorization Code flow with available authentication methods in Issuer API
            val authorizationCodeFlow = AuthorizationCodeFlow(testHttpClient(doFollowRedirects = false))
            authorizationCodeFlow.testIssuerAPI()

        }
    }



    //@Test // enable to execute test selectively
    fun lspIssuanceTests() = runTest(timeout = 5.minutes) {
        val client = testHttpClient(doFollowRedirects = false)
        testBlock {
          val lspPotentialIssuance = LspPotentialIssuance(client)
          lspPotentialIssuance.testTrack1()
          lspPotentialIssuance.testTrack2()
        }
    }

    // @Test
    fun lspVerifierTests() = runTest(timeout = 5.minutes) {
        val client = testHttpClient(doFollowRedirects = false)
        testBlock {
          val lspPotentialVerification = LspPotentialVerification(client)
          lspPotentialVerification.testPotentialInteropTrack3()
          lspPotentialVerification.testPotentialInteropTrack4()
        }
    }

  suspend fun setupTestWallet(): LspPotentialWallet {
    var client = testHttpClient()
    client.post("/wallet-api/auth/login") {
      setBody(
        EmailAccountRequest(
          email = "user@email.com", password = "password"
        ) as AccountRequest
      )
    }.expectSuccess().apply {
      body<JsonObject>().let { result ->
        assertNotNull(result["token"])
        val token = result["token"]!!.jsonPrimitive.content.expectLooksLikeJwt()

        client = testHttpClient(token = token)
      }
    }
    val walletId = client.get("/wallet-api/wallet/accounts/wallets").expectSuccess()
        .body<AccountWalletListing>().wallets.first().id.toString()
    return LspPotentialWallet(client, walletId)
  }

  //@Test // enable to execute test selectively
  fun lspWalletTests() = runTest(timeout = 5.minutes) {
    testBlock {
      val lspPotentialWallet = setupTestWallet()
      lspPotentialWallet.testMDocIssuance()
      lspPotentialWallet.testMdocPresentation()

      lspPotentialWallet.testSDJwtVCIssuance()
      lspPotentialWallet.testSDJwtPresentation()
    }
  }

    private fun testHttpClient(token: String? = null, doFollowRedirects: Boolean = true) = HttpClient(CIO) {
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
      followRedirects = doFollowRedirects
    }
}

fun String.expectLooksLikeJwt(): String =
    also { assert(startsWith("ey") && count { it == '.' } == 2) { "Does not look like JWT" } }

suspend fun HttpResponse.expectSuccess(): HttpResponse = also {
    assert(status.isSuccess()) { "HTTP status is non-successful for response: $it, body is ${it.bodyAsText()}" }
}

fun HttpResponse.expectRedirect(): HttpResponse = also {
    assert(status == HttpStatusCode.Found) { "HTTP status is non-successful" }
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
