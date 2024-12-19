@file:OptIn(ExperimentalUuidApi::class)

package presentationexchange

import AuthApi
import CredentialsApi
import DidsApi
import KeysApi
import WaltidServicesE2ETests
import expectSuccess
import id.walt.commons.testing.E2ETest.test
import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyType
import id.walt.verifier.oidc.PresentationSessionInfo
import id.walt.webwallet.db.models.AccountWalletListing
import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.db.models.WalletDid
import id.walt.webwallet.service.keys.SingleKeyResponse
import id.walt.webwallet.web.controllers.exchange.UsePresentationRequest
import id.walt.webwallet.web.model.AccountRequest
import id.walt.webwallet.web.model.EmailAccountRequest
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.util.*
import kotlinx.serialization.json.jsonPrimitive
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class PresentationDefinitionPolicyTests {

    private val email = "${Uuid.random()}@mail.com"
    private val password = Uuid.random().toString()


    private lateinit var walletUuid: Uuid
    private lateinit var walletId: String
    private lateinit var did: String
    private lateinit var keyId: String

    private var client: HttpClient = WaltidServicesE2ETests.testHttpClient()

    private suspend fun createWalletAccount() {
        val authApi = AuthApi(client)
        val accountRequest = EmailAccountRequest(
            email = email,
            password = password,
        ) as AccountRequest
        authApi.register(
            request = accountRequest,
        )
    }

    private suspend fun loginWallet() {
        val authApi = AuthApi(client)
        authApi.login(
            request = EmailAccountRequest(
                email = email,
                password = password,
            ) as AccountRequest,
        ) {
            client = WaltidServicesE2ETests.testHttpClient(token = it["token"]!!.jsonPrimitive.content)
        }
    }

    private suspend fun getAccountWalletId() {
        client.get("/wallet-api/wallet/accounts/wallets").expectSuccess().apply {
            val listing = body<AccountWalletListing>()
            assert(listing.wallets.isNotEmpty()) { "No wallets available!" }
            walletId = listing.wallets.first().id.toString()
            walletUuid = Uuid.parse(walletId)
        }
    }

    private suspend fun deleteWalletCredentials() {
        val credentialsApi = CredentialsApi(client)
        client.get("/wallet-api/wallet/$walletId/credentials")
            .expectSuccess()
            .body<List<WalletCredential>>()
            .forEach {
                credentialsApi.delete(
                    walletUuid,
                    it.id,
                    true,
                )
            }
    }

    private suspend fun deleteWalletKeys() {
        val keysApi = KeysApi(client)
        client.get("/wallet-api/wallet/$walletId/keys")
            .expectSuccess()
            .body<List<SingleKeyResponse>>()
            .forEach {
                keysApi.delete(walletUuid, it.keyId.id)
            }
    }

    private suspend fun deleteWalletDids() {
        val didsApi = DidsApi(client)
        client.get("/wallet-api/wallet/$walletId/dids")
            .expectSuccess()
            .body<List<WalletDid>>()
            .forEach {
                didsApi.delete(walletUuid, it.did)
            }
    }

    private suspend fun clearAllWalletData() {
        deleteWalletKeys()
        deleteWalletDids()
        deleteWalletCredentials()
    }

    private suspend fun createKey() {
        val keysApi = KeysApi(client)
        keysApi.generate(
            wallet = walletUuid,
            request = KeyGenerationRequest(
                backend = "jwk",
                keyType = KeyType.secp256r1,
            ),
        ) {
            keyId = it
        }
    }

    private suspend fun createDid() {
        val didsApi = DidsApi(client)
        didsApi.create(
            wallet = walletUuid,
            payload = DidsApi.DidCreateRequest(
                method = "jwk",
                keyId = keyId,
            ),
        ) {
            did = it
        }
    }

    private suspend fun getWalletCredentials() = client.get("/wallet-api/wallet/$walletId/credentials")
        .expectSuccess()
        .body<List<WalletCredential>>()


    private suspend fun setupTestCases() {
        createWalletAccount()
        loginWallet()
        getAccountWalletId()
        clearAllWalletData()
        createKey()
        createDid()
    }

    suspend fun runTests() {
        setupTestCases()
        runTestScenario(
            description = "Presentation Definition Policy Scenario #1 - JWT VC JSON University Degree, " +
                    "no selectively disclosable claims, " +
                    "vc prefix in JSON paths",
            setup = {
                issueCredentialsToWallet(
                    issuanceRequests = listOf(
                        IssuanceRequests.universityDegree,
                    )
                )
            },
            evaluate = {
                evaluatePresentationVerificationResult(
                    presentationRequest = PresentationRequests.w3cVcTypeCorrectJsonPath,
                    expectedVerificationResult = true,
                )
            },
            cleanup = {
                deleteWalletCredentials()
            },
        )

        runTestScenario(
            description = "Presentation Definition Policy Scenario #2 - JWT VC JSON University Degree, " +
                    "no selectively disclosable claims, " +
                    "NO vc prefix in JSON paths",
            setup = {
                issueCredentialsToWallet(
                    issuanceRequests = listOf(
                        IssuanceRequests.universityDegree,
                    )
                )
            },
            evaluate = {
                evaluatePresentationVerificationResult(
                    presentationRequest = PresentationRequests.w3cVcTypeInvalidJsonPath,
                    expectedVerificationResult = false,
                )
            },
            cleanup = {
                deleteWalletCredentials()
            },
        )

        runTestScenario(
            description = "OSS User issue",
            setup = {
                issueCredentialsToWallet(
                    issuanceRequests = listOf(
                        IssuanceRequests.universityDegree,
                    )
                )
            },
            evaluate = {
                evaluatePresentationVerificationResult(
                    presentationRequest = PresentationRequests.outOfOrderPresentationRequest1,
                    expectedVerificationResult = false,
                )
            },
            cleanup = {
            },
        )

        runTestScenario(
            description = "OSS User issue",
            setup = {
                issueCredentialsToWallet(
                    issuanceRequests = listOf(
                        IssuanceRequests.universityDegree,
                    )
                )
            },
            evaluate = {
                evaluatePresentationVerificationResult(
                    presentationRequest = PresentationRequests.outOfOrderPresentationRequest2,
                    expectedVerificationResult = false,
                )
            },
            cleanup = {
            },
        )
    }

    private suspend fun issueCredentialsToWallet(
        issuanceRequests: List<String>,
    ) {
        issuanceRequests.forEach { request ->
            client.post("/openid4vc/jwt/issue") {
                setBody(request)
            }.expectSuccess()
                .body<String>()
                .let { offerUrl ->
                    client.post("/wallet-api/wallet/$walletId/exchange/useOfferRequest") {
                        setBody(offerUrl)
                    }.expectSuccess()
                        .body<List<WalletCredential>>()
                        .let {
                            assert(it.isNotEmpty())
                        }
                }
        }
    }

    private suspend fun evaluatePresentationVerificationResult(
        presentationRequest: String,
        expectedVerificationResult: Boolean,
    ) {
        val presentationUrl = client.post("/openid4vc/verify") {
            setBody(presentationRequest)
        }.expectSuccess().body<String>()
        val presentationSessionId = Url(presentationUrl).parameters.getOrFail("state")

        val credentialIds = getWalletCredentials().map { it.id }
        client.post("/wallet-api/wallet/$walletId/exchange/usePresentationRequest") {
            setBody(
                UsePresentationRequest(
                    did = did,
                    presentationRequest = presentationUrl,
                    selectedCredentials = credentialIds,
                )
            )
        }

        client
            .get("/openid4vc/session/$presentationSessionId")
            .expectSuccess()
            .body<PresentationSessionInfo>()
            .let {
                assert(it.verificationResult == expectedVerificationResult)
            }
    }

    private suspend fun runTestScenario(
        description: String,
        setup: suspend () -> Unit,
        evaluate: suspend () -> Unit,
        cleanup: suspend () -> Unit,
    ) {
        setup()
        test(
            name = description,
        ) {
            evaluate()
        }
        cleanup()
    }
}