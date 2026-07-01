package id.walt.policies.policies

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyMeta
import id.walt.crypto.keys.KeyType
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JWTCryptoProviderManagerTest {

    @Test
    fun `default async provider signs and verifies through key interface`() = runTest {
        val key = RecordingKey("test-key")
        val provider = JWTCryptoProviderManager.getDefaultAsyncJWTCryptoProvider(mapOf("test-key" to key))
        val payload = buildJsonObject { put("sub", "alice") }

        val jwt = provider.sign(payload, typ = "JWT", headers = mapOf("cty" to "vc"))
        val result = provider.verify(jwt)

        assertEquals(jwtWithKid("test-key"), jwt)
        assertEquals("""{"sub":"alice"}""", key.signedPlaintext!!.decodeToString())
        assertEquals(JsonPrimitive("test-key"), key.signedHeaders!!["kid"])
        assertEquals(JsonPrimitive("JWT"), key.signedHeaders!!["typ"])
        assertEquals(JsonPrimitive("vc"), key.signedHeaders!!["cty"])
        assertEquals(jwtWithKid("test-key"), key.verifiedJwt)
        assertTrue(result.verified)
    }

    @Test
    fun `default async provider does not guess when jwt kid is unknown`() = runTest {
        val provider = JWTCryptoProviderManager.getDefaultAsyncJWTCryptoProvider(
            mapOf("test-key" to RecordingKey("test-key"))
        )

        assertFailsWith<IllegalArgumentException> {
            provider.verify(jwtWithKid("other-key"))
        }
    }

    private class RecordingKey(private val keyId: String) : Key() {
        var signedPlaintext: ByteArray? = null
        var signedHeaders: Map<String, JsonElement>? = null
        var verifiedJwt: String? = null

        override val keyType: KeyType = KeyType.secp256r1
        override val hasPrivateKey: Boolean = true

        override suspend fun getKeyId(): String = keyId
        override suspend fun getThumbprint(): String = "thumbprint"
        override suspend fun exportJWK(): String = "{}"
        override suspend fun exportJWKObject(): JsonObject = JsonObject(emptyMap())
        override suspend fun exportPEM(): String = ""

        override suspend fun signRaw(
            plaintext: ByteArray,
            customSignatureAlgorithm: String?
        ): Any = error("not used")

        override suspend fun signJws(
            plaintext: ByteArray,
            headers: Map<String, JsonElement>
        ): String {
            signedPlaintext = plaintext
            signedHeaders = headers
            return jwtWithKid(keyId)
        }

        override suspend fun verifyRaw(
            signed: ByteArray,
            detachedPlaintext: ByteArray?,
            customSignatureAlgorithm: String?
        ): Result<ByteArray> = error("not used")

        override suspend fun verifyJws(signedJws: String): Result<JsonElement> {
            verifiedJwt = signedJws
            return Result.success(JsonObject(emptyMap()))
        }

        override suspend fun getPublicKey(): Key = this
        override suspend fun getPublicKeyRepresentation(): ByteArray = ByteArray(0)
        override suspend fun getMeta(): KeyMeta = error("not used")
        override suspend fun deleteKey(): Boolean = false
    }

    private companion object {
        fun jwtWithKid(kid: String): String {
            val header = buildJsonObject { put("kid", kid) }.toString().encodeToByteArray().encodeToBase64Url()
            return "$header.payload.signature"
        }
    }
}
