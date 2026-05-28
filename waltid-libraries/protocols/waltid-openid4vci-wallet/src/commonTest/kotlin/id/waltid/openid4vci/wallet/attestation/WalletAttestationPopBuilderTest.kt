package id.waltid.openid4vci.wallet.attestation

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyMeta
import id.walt.crypto.keys.KeyType
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private class PopTestP256Key : Key() {
    override val keyType: KeyType = KeyType.secp256r1
    override val hasPrivateKey: Boolean = true
    override suspend fun getKeyId(): String = "mock-p256-kid"
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

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun signJws(plaintext: ByteArray, headers: Map<String, JsonElement>): String {
        val headerJson = JsonObject(headers).toString()
        val headerB64 = Base64.UrlSafe.encode(headerJson.encodeToByteArray()).trimEnd('=')
        val payloadB64 = Base64.UrlSafe.encode(plaintext).trimEnd('=')
        return "$headerB64.$payloadB64.mock-sig"
    }
}

private class PopTestEd25519Key : Key() {
    override val keyType: KeyType = KeyType.Ed25519
    override val hasPrivateKey: Boolean = true
    override suspend fun getKeyId(): String = "mock-ed-kid"
    override suspend fun getThumbprint(): String = "mock-thumbprint"
    override suspend fun exportJWK(): String = """{"kty":"OKP","crv":"Ed25519","x":"x"}"""
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

class WalletAttestationPopBuilderTest {

    private val builder = WalletAttestationPopBuilder()

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun testPopJwtStructure() = runTest {
        val key = PopTestP256Key()
        val jwt = builder.buildPopJwt(key, "wallet-client", "https://issuer.example.com/token")

        val parts = jwt.split(".")
        assertEquals(3, parts.size, "JWT should have 3 parts")

        val b64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

        val header = Json.parseToJsonElement(
            b64.decode(parts[0]).decodeToString()
        ).jsonObject
        assertEquals("oauth-client-attestation-pop+jwt", header["typ"]?.jsonPrimitive?.content)
        assertEquals("ES256", header["alg"]?.jsonPrimitive?.content)

        val payload = Json.parseToJsonElement(
            b64.decode(parts[1]).decodeToString()
        ).jsonObject
        assertEquals("wallet-client", payload["iss"]?.jsonPrimitive?.content)
        assertEquals("https://issuer.example.com/token", payload["aud"]?.jsonPrimitive?.content)
        assertNotNull(payload["iat"])
        assertNotNull(payload["exp"])
        assertNotNull(payload["jti"])

        val iat = payload["iat"]!!.jsonPrimitive.content.toLong()
        val exp = payload["exp"]!!.jsonPrimitive.content.toLong()
        assertTrue(exp - iat == 300L, "exp should be iat + 300")
    }

    @Test
    fun testRejectsNonP256Key() = runTest {
        val key = PopTestEd25519Key()
        val error = assertFailsWith<IllegalStateException> {
            builder.buildPopJwt(key, "client", "https://issuer.example.com/token")
        }
        assertTrue(error.message!!.contains("P-256"))
    }
}
