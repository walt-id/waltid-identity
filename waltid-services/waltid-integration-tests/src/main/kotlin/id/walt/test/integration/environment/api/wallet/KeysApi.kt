@file:OptIn(ExperimentalUuidApi::class)

package id.walt.test.integration.environment.api.wallet

import id.walt.commons.testing.E2ETest
import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.test.integration.expectSuccess
import id.walt.test.integration.tryGetData
import id.walt.webwallet.service.keys.SingleKeyResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class KeysApi(private val e2e: E2ETest, private val client: HttpClient) {


    suspend fun listRaw(walletId: Uuid): HttpResponse =
        client.get("/wallet-api/wallet/$walletId/keys")

    suspend fun list(walletId: Uuid): List<SingleKeyResponse> = listRaw(walletId).let {
        it.expectSuccess()
        it.body<List<SingleKeyResponse>>()
    }

    suspend fun generateRaw(walletId: Uuid, request: KeyGenerationRequest) =
        client.post("/wallet-api/wallet/$walletId/keys/generate") {
            setBody(request)
        }

    suspend fun generate(walletId: Uuid, request: KeyGenerationRequest): String =
        generateRaw(walletId, request).let {
            it.expectSuccess()
            val generatedKeyId = it.body<String>()
            assertFalse(generatedKeyId.isEmpty(), "Empty key id is returned!")
            generatedKeyId
        }

    suspend fun loadRaw(walletId: Uuid, keyId: String) =
        client.get("/wallet-api/wallet/$walletId/keys/$keyId/load")

    suspend fun load(walletId: Uuid, keyId: String) =
        loadRaw(walletId, keyId).let {
            it.expectSuccess()
            it.body<JsonElement>()
        }

    suspend fun loadMetaRaw(wallet: Uuid, keyId: String) =
        client.get("/wallet-api/wallet/$wallet/keys/$keyId/meta")


    suspend fun loadMeta(wallet: Uuid, keyId: String): JsonObject =
        loadMetaRaw(wallet, keyId).let {
            val response = it.body<JsonObject>()
            assertNotNull(response.tryGetData("keyId")?.jsonPrimitive?.content) { "Missing _keyId_ component!" }
            assert(response.tryGetData("keyId")?.jsonPrimitive?.content == keyId) { "Wrong _keyId_ value!" }
            assertNotNull(response.tryGetData("type")?.jsonPrimitive?.content) { "Missing _type_ component!" }
            response
        }


    suspend fun exportKeyRaw(walletId: Uuid, keyId: String, format: String, isPrivate: Boolean) =
        client.get("/wallet-api/wallet/$walletId/keys/$keyId/export") {
            url {
                parameters.append("format", format)
                parameters.append("loadPrivateKey", "$isPrivate")
            }
        }

    suspend fun exportKey(walletId: Uuid, keyId: String, format: String, isPrivate: Boolean): JsonObject =
        exportKeyRaw(walletId, keyId, format, isPrivate).let {
            it.expectSuccess()
            // TODO: It seems response ContentType header is wrong - I think that is the reason,
            // TODO: why response decoding needs to be done that wierd way
            Json.decodeFromString(it.body<String>())
        }

    suspend fun deleteKeyRaw(walletId: Uuid, keyId: String) =
        client.delete("/wallet-api/wallet/$walletId/keys/$keyId")

    suspend fun deleteKey(wallet: Uuid, keyId: String) {
        deleteKeyRaw(wallet, keyId).expectSuccess()
    }

    suspend fun importKeyRaw(walletId: Uuid, payload: String) =
        client.post("/wallet-api/wallet/$walletId/keys/import") {
            setBody(payload)
        }

    suspend fun importKey(wallet: Uuid, payload: String): String =
        importKeyRaw(wallet, payload).let {
            it.expectSuccess()
            it.body<String>()
        }
}
