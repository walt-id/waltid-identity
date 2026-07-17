package id.waltid.openid4vci.wallet.attestation

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyMeta
import id.walt.crypto.keys.KeyType
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

private class GenericProviderTestKey : Key() {
    override val keyType: KeyType = KeyType.secp256r1
    override val hasPrivateKey: Boolean = true
    override suspend fun getKeyId(): String = "mock-kid"
    override suspend fun getThumbprint(): String = "mock-thumbprint"
    override suspend fun exportJWK(): String = """{"kty":"EC","crv":"P-256","x":"x","y":"y"}"""
    override suspend fun exportJWKObject(): JsonObject = JsonObject(emptyMap())
    override suspend fun getPublicKey(): Key = this
    override suspend fun getMeta(): KeyMeta = throw NotImplementedError()
    override suspend fun deleteKey(): Boolean = true
    override suspend fun exportPEM(): String = ""
    override suspend fun signRaw(plaintext: ByteArray, customSignatureAlgorithm: String?): Any = byteArrayOf()
    override suspend fun verifyRaw(signed: ByteArray, detachedPlaintext: ByteArray?, customSignatureAlgorithm: String?): Result<ByteArray> = Result.success(byteArrayOf())
    override suspend fun verifyJws(signedJws: String): Result<JsonElement> = Result.success(JsonObject(emptyMap()))
    override suspend fun getPublicKeyRepresentation(): ByteArray = byteArrayOf()
    override suspend fun signJws(plaintext: ByteArray, headers: Map<String, JsonElement>): String = "mock.jwt.sig"
}

class GenericHttpWalletAttestationProviderTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testConfiguredJwkRequestBody() = runTest {
        var capturedBody: JsonObject? = null
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    assertEquals(
                        "https://wallet-provider.example.com/wallet-instance-attestation/jwk",
                        request.url.toString(),
                    )
                    capturedBody = json.parseToJsonElement(request.bodyText()).jsonObject
                    respond(
                        content = """{"walletInstanceAttestation":"attestation.jwt"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            }
        }

        val provider = GenericHttpWalletAttestationProvider(
            attesterUrl = "https://wallet-provider.example.com/wallet-instance-attestation/jwk",
            httpClient = client,
            requestBodyTemplate = buildJsonObject {
                put("jwk", PUBLIC_JWK_PLACEHOLDER)
            },
        )

        val jwt = provider.getAttestationJwt(GenericProviderTestKey(), "wallet-client")

        assertEquals("attestation.jwt", jwt)
        val jwk = capturedBody!!["jwk"]!!.jsonObject
        assertEquals("EC", jwk["kty"]?.jsonPrimitive?.content)
        assertEquals("P-256", jwk["crv"]?.jsonPrimitive?.content)
    }

    private fun HttpRequestData.bodyText(): String =
        when (val requestBody = body) {
            is OutgoingContent.ByteArrayContent -> requestBody.bytes().decodeToString()
            is TextContent -> requestBody.text
            else -> error("Unsupported request body type: ${requestBody::class}")
        }
}
