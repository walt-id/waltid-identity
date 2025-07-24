@file:OptIn(ExperimentalUuidApi::class)

import id.walt.commons.testing.E2ETest
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

class MDocPreparedWallet(val e2e: E2ETest) {

    private val email = "${Uuid.random()}@walt.id"

    private val password = Uuid.random().toString()

    lateinit var walletClient: HttpClient

    lateinit var walletId: String

    lateinit var keyId: String

    lateinit var did: String

    private suspend fun createNewWallet() {
        val tempClient = e2e.testHttpClient()
        tempClient.post("/wallet-api/auth/register") {
            setBody(
                EmailAccountRequest(
                    email = email,
                    password = password,
                ) as AccountRequest
            )
        }.expectSuccess()

        tempClient.post("/wallet-api/auth/login") {
            setBody(
                EmailAccountRequest(
                    email = email,
                    password = password,
                ) as AccountRequest
            )
        }.expectSuccess().body<JsonObject>().let {
            walletClient = e2e.testHttpClient(it["token"]!!.jsonPrimitive.content)
        }

        walletClient.get("/wallet-api/wallet/accounts/wallets")
            .expectSuccess().body<AccountWalletListing>().let {
                walletId = it.wallets.first().id.toString()
            }
    }

    private suspend fun clearAllKeys() {
        val walletKeys = walletClient.get("/wallet-api/wallet/${walletId}/keys")
            .expectSuccess().body<List<SingleKeyResponse>>()

        walletKeys.forEach { walletKey ->
            walletClient.delete("/wallet-api/wallet/${walletId}/keys/${walletKey.keyId.id}")
                .expectSuccess()
        }
    }

    private suspend fun clearAllDids() {
        val walletDids = walletClient.get("/wallet-api/wallet/${walletId}/dids")
            .expectSuccess().body<List<WalletDid>>()

        walletDids.forEach { walletDid ->
            walletClient.delete("/wallet-api/wallet/${walletId}/dids/${walletDid.did}")
                .expectSuccess()
        }
    }

    private suspend fun generateSecp256r1Key() {
        keyId = walletClient.post("/wallet-api/wallet/${walletId}/keys/generate") {
            setBody(
                KeyGenerationRequest(
                    keyType = KeyType.secp256r1,
                )
            )
        }.expectSuccess().bodyAsText()
    }

    private suspend fun generateDidBackedBySecp256r1Key() {
        did = walletClient.post("/wallet-api/wallet/${walletId}/dids/create/jwk") {
            url {
                parameters.append("keyId", keyId)
            }
        }.expectSuccess().bodyAsText()

        walletClient.post("/wallet-api/wallet/${walletId}/dids/default") {
            url {
                parameters.append("did", did)
            }
        }.expectSuccess()
    }

    companion object {
        private const val TEST_SUITE = "MDoc Prepared/Ready Wallet Setup"
    }

    suspend fun testWalletSetup() {
        val wallet = MDocPreparedWallet(e2e)

        e2e.test(
            name = "$TEST_SUITE - Create New Wallet"
        ) {
            wallet.createNewWallet()
        }

        e2e.test(
            name = "$TEST_SUITE - Clear All Keys"
        ) {
            wallet.clearAllKeys()
        }

        e2e.test(
            name = "$TEST_SUITE - Clear All Dids"
        ) {
            wallet.clearAllDids()
        }

        e2e.test(
            name = "$TEST_SUITE - Generate Secp256r1 Key"
        ) {
            wallet.generateSecp256r1Key()
        }

        e2e.test(
            name = "$TEST_SUITE - Generate did:jwk Backed By Sepc256r1 Key "
        ) {
            wallet.generateDidBackedBySecp256r1Key()
        }
    }

    suspend fun createSetupWallet(): MDocPreparedWallet {
        val wallet = MDocPreparedWallet(e2e)
        wallet.createNewWallet()
        wallet.clearAllKeys()
        wallet.clearAllDids()
        wallet.generateSecp256r1Key()
        wallet.generateDidBackedBySecp256r1Key()
        return wallet
    }
}
