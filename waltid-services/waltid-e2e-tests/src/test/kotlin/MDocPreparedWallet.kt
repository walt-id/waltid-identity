@file:OptIn(ExperimentalUuidApi::class)

import id.walt.commons.testing.E2ETest.testHttpClient
import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyType
import id.walt.webwallet.db.models.AccountWalletListing
import id.walt.webwallet.db.models.WalletDid
import id.walt.webwallet.service.keys.SingleKeyResponse
import id.walt.webwallet.web.model.AccountRequest
import id.walt.webwallet.web.model.EmailAccountRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.collections.forEach
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class MDocPreparedWallet {

    private val email = "${Uuid.random()}@walt.id"

    private val password = Uuid.random().toString()

    lateinit var walletClient: HttpClient

    lateinit var walletId: String

    private lateinit var keyId: String

    private lateinit var did: String

    private suspend fun createNewWallet() {
        val tempClient = testHttpClient()
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
            walletClient = testHttpClient(it["token"]!!.jsonPrimitive.content)
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
}