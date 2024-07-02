import E2ETestWebService.test
import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyType
import id.walt.webwallet.service.keys.SingleKeyResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.uuid.UUID
import kotlin.test.assertNotNull

class KeysApi(private val client: HttpClient) {

    suspend fun list(wallet: UUID, expected: KeyGenerationRequest) =
        test("/wallet-api/wallet/{wallet}/keys - get keys") {
            client.get("/wallet-api/wallet/$wallet/keys").expectSuccess().apply {
                val listing = body<List<SingleKeyResponse>>()
                assert(listing.isNotEmpty()) { "No default key was created!" }
                assert(KeyType.valueOf(listing[0].algorithm) == expected.keyType) { "Default key type not ${expected.keyType}" }
            }
        }

    suspend fun generate(wallet: UUID, request: KeyGenerationRequest, output: ((String) -> Unit)? = null) =
        test("/wallet-api/wallet/{wallet}/keys/generate - generate key") {
            client.post("/wallet-api/wallet/$wallet/keys/generate") {
                setBody(request)
            }.expectSuccess().apply {
                val generatedKeyId = body<String>()
                assert(generatedKeyId.isNotEmpty()) { "Empty key id is returned!" }
                output?.invoke(generatedKeyId)
            }
        }

    suspend fun load(wallet: UUID, keyId: String, expected: KeyGenerationRequest) =
        test("/wallet-api/wallet/{wallet}/keys/{keyId}/load - load key") {
            client.get("/wallet-api/wallet/$wallet/keys/$keyId/load").expectSuccess().apply {
                val response = body<JsonElement>()
                assertKeyComponents(response, keyId, expected.keyType, true)
            }
        }

    suspend fun meta(wallet: UUID, keyId: String, expected: KeyGenerationRequest) =
        test("/wallet-api/wallet/{wallet}/keys/{keyId}/meta - key meta") {
            client.get("/wallet-api/wallet/$wallet/keys/$keyId/meta").expectSuccess().apply {
                val response = body<JsonElement>()
                assertNotNull(response.tryGetData("keyId")?.jsonPrimitive?.content) { "Missing _keyId_ component!" }
                assert(response.tryGetData("keyId")?.jsonPrimitive?.content == keyId) { "Wrong _keyId_ value!" }
                assertNotNull(response.tryGetData("type")?.jsonPrimitive?.content) { "Missing _type_ component!" }
                when (expected.backend) {
                    "jwt" -> assert(response.tryGetData("type")!!.jsonPrimitive.content.endsWith("JwkKeyMeta")) { "Missing _type_ component!" }
                    "tse" -> TODO()
                    "oci" -> TODO()
                    "oci-rest-api" -> TODO()
                    else -> Unit
                }
            }
        }

    suspend fun export(
        wallet: UUID, keyId: String, format: String, isPrivate: Boolean, expected: KeyGenerationRequest
    ) = test("/wallet-api/wallet/{wallet}/keys/{keyId}/export - export key") {
        client.get("/wallet-api/wallet/$wallet/keys/$keyId/export") {
            url {
                parameters.append("format", format)
                parameters.append("loadPrivateKey", "$isPrivate")
            }
        }.expectSuccess().apply {
            val response = Json.decodeFromString<JsonElement>(body<String>())
            assertKeyComponents(response, keyId, expected.keyType, isPrivate)
        }
    }

    suspend fun delete(wallet: UUID, keyId: String) = test("/wallet-api/wallet/{wallet}/keys/{keyId} - delete key") {
        client.delete("/wallet-api/wallet/$wallet/keys/$keyId").expectSuccess()
    }

    suspend fun import(wallet: UUID, payload: String) = test("/wallet-api/wallet/{wallet}/keys/import - import key") {
        client.post("/wallet-api/wallet/$wallet/keys/import") {
            setBody(payload)
        }.expectSuccess()
    }
}