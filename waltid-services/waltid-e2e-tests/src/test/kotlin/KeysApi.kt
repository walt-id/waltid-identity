import id.walt.commons.config.ConfigManager
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

class KeysApi(private val client: HttpClient, val wallet: UUID) {

    suspend fun list(wallet: UUID, expected: KeyGenerationRequest?) =
        test("/wallet-api/wallet/{wallet}/keys - get keys") {
            client.get("/wallet-api/wallet/$wallet/keys").expectSuccess().apply {
                val listing = body<List<SingleKeyResponse>>()
                when (expected) {
                    null -> assertNoDefaultKey(listing)
                    else -> assertDefaultKey(listing, expected)
                }
            }
        }

    suspend fun generate(request: KeyGenerationRequest, output: ((String) -> Unit)? = null) =
        client.post("/wallet-api/wallet/$wallet/keys/generate") {
            setBody(request)
        }.expectSuccess().run {
            val generatedKeyId = body<String>()
            assert(generatedKeyId.isNotEmpty()) { "Empty key id is returned!" }

            generatedKeyId
        }

    suspend fun load(keyId: String, expected: KeyGenerationRequest) =
        client.get("/wallet-api/wallet/$wallet/keys/$keyId/load").expectSuccess().apply {
            val response = body<JsonElement>()
            assertKeyComponents(response, keyId, expected.keyType, true)
        }

    suspend fun meta(keyId: String, expected: KeyGenerationRequest) =
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

    suspend fun export(
        keyId: String, format: String, isPrivate: Boolean, expected: KeyGenerationRequest,
    ) =
        client.get("/wallet-api/wallet/$wallet/keys/$keyId/export") {
            url {
                parameters.append("format", format)
                parameters.append("loadPrivateKey", "$isPrivate")
            }
        }.expectSuccess().apply {
            val response = Json.decodeFromString<JsonElement>(body<String>())
            assertKeyComponents(response, keyId, expected.keyType, isPrivate)
        }

    suspend fun delete(keyId: String) =
        client.delete("/wallet-api/wallet/$wallet/keys/$keyId").expectSuccess()

    suspend fun import(payload: String) =
        client.post("/wallet-api/wallet/$wallet/keys/import") {
            setBody(payload)
        }.expectSuccess()
}
