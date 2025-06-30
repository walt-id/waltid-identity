@file:OptIn(ExperimentalUuidApi::class)

import id.walt.commons.testing.E2ETest.test
import id.walt.commons.testing.E2ETest.testHttpClient
import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyType
import id.walt.webwallet.db.models.AccountWalletListing
import id.walt.webwallet.db.models.WalletDid
import id.walt.webwallet.service.keys.SingleKeyResponse
import id.walt.webwallet.web.model.AccountRequest
import id.walt.webwallet.web.model.EmailAccountRequest
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class VerifierPresentedCredentialsTests {

    private val TEST_SUITE = "Verifier Presented Credentials Test Suite"

    private val email = "${Uuid.random()}@walt.id"

    private val password = Uuid.random().toString()

    private lateinit var walletUuid: Uuid

    private lateinit var keyId: String

    private lateinit var did: String

    private lateinit var walletClient: HttpClient

    private val client = testHttpClient()

    private suspend fun setupTestSuite() {
        createWallet()
        deleteAllKeys()
        deleteAllDids()
        createSecp256r1Key()
        createDefaultDid()
    }

    private suspend fun createWallet() =
        test(
            name = "${TEST_SUITE}: Setup step #1: Create a wallet"
        ) {

            client.post("/wallet-api/auth/register") {
                setBody(
                    EmailAccountRequest(
                        email = email,
                        password = password,
                    ) as AccountRequest
                )
            }.expectSuccess()

            client.post("/wallet-api/auth/login") {
                setBody(
                    EmailAccountRequest(
                        email = email,
                        password = password,
                    ) as AccountRequest
                )
            }.expectSuccess().body<JsonObject>().let {
                walletClient = testHttpClient(it["token"]!!.jsonPrimitive.content)
            }

            walletClient.get("/wallet-api/wallet/accounts/wallets")
                .expectSuccess().body<AccountWalletListing>().let {
                    walletUuid = it.wallets.first().id
                }
        }

    private suspend fun deleteAllKeys() =
        test(
            name = "${TEST_SUITE}: Setup step #2: Delete default generated key (Ed25519 while we need sepc256r1)"
        ) {
            val walletKeys = walletClient.get("/wallet-api/wallet/${walletUuid}/keys")
                .expectSuccess().body<List<SingleKeyResponse>>()

            walletKeys.forEach { walletKey ->
                walletClient.delete("/wallet-api/wallet/${walletUuid}/keys/${walletKey.keyId.id}")
                    .expectSuccess()
            }
        }

    private suspend fun deleteAllDids() =
        test(
            name = "${TEST_SUITE}: Setup step #3: Delete default generated did (backed by a non-appropriate key)"
        ) {
            val walletDids = walletClient.get("/wallet-api/wallet/${walletUuid}/dids")
                .expectSuccess().body<List<WalletDid>>()

            walletDids.forEach { walletDid ->
                walletClient.delete("/wallet-api/wallet/${walletUuid}/dids/${walletDid.did}")
                    .expectSuccess()
            }
        }

    private suspend fun createSecp256r1Key() =
        test(
            name = "${TEST_SUITE}: Setup step #4: Create secp256r1 key"
        ) {
            keyId = walletClient.post("/wallet-api/wallet/${walletUuid}/keys/generate") {
                setBody(
                    KeyGenerationRequest(
                        keyType = KeyType.secp256r1,
                    )
                )
            }.expectSuccess().bodyAsText()
        }

    private suspend fun createDefaultDid() =
        test(
            name = "${TEST_SUITE}: Setup step #5: Create new did:jwk and mark it as default"
        ) {
            did = walletClient.post("/wallet-api/wallet/${walletUuid}/dids/create/jwk") {
                url {
                    parameters.append("keyId", keyId)
                }
            }.expectSuccess().bodyAsText()

            walletClient.post("/wallet-api/wallet/${walletUuid}/dids/default") {
                url {
                    parameters.append("did", did)
                }
            }.expectSuccess()
        }


    suspend fun runTests() {
        setupTestSuite()
    }
}