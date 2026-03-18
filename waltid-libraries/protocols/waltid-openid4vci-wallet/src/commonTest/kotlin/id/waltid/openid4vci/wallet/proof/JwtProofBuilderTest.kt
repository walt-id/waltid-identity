package id.waltid.openid4vci.wallet.proof

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyMeta
import id.walt.crypto.keys.KeyType
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MockKey(
    val kid: String? = null,
    override val keyType: KeyType = KeyType.Ed25519,
) : Key() {
    override suspend fun getKeyId(): String = kid ?: "mock-kid"
    override suspend fun getThumbprint(): String = "mock-thumbprint"
    override suspend fun exportJWK(): String = """{"kty":"OKP","crv":"Ed25519","x":"..."}"""
    override suspend fun exportJWKObject(): JsonObject = JsonObject(emptyMap())
    override val hasPrivateKey: Boolean = true
    override suspend fun signJws(plaintext: ByteArray, headers: Map<String, JsonElement>): String = "mock.jwt.proof"
    override suspend fun getPublicKey(): Key = this
    override suspend fun getMeta(): KeyMeta = throw NotImplementedError()
    override suspend fun deleteKey(): Boolean = true
    override suspend fun exportPEM(): String = "mock-pem"
    override suspend fun signRaw(plaintext: ByteArray, customSignatureAlgorithm: String?): Any = byteArrayOf()
    override suspend fun verifyRaw(
        signed: ByteArray,
        detachedPlaintext: ByteArray?,
        customSignatureAlgorithm: String?,
    ): Result<ByteArray> = Result.success(byteArrayOf())

    override suspend fun verifyJws(signedJws: String): Result<JsonElement> = Result.success(JsonObject(emptyMap()))
    override suspend fun getPublicKeyRepresentation(): ByteArray = byteArrayOf()
}

class JwtProofBuilderTest {

    private val builder = JwtProofBuilder()
    private val audience = "https://issuer.example.com"
    private val nonce = "test-nonce"

    @Test
    fun testBuildJwtProofWithKid() = runTest {
        val keyId = "did:key:123"
        val mockKey = MockKey(kid = keyId)

        val proof = builder.buildJwtProof(
            key = mockKey,
            audience = audience,
            nonce = nonce,
            keyId = keyId
        )

        assertNotNull(proof.jwt)
        assertEquals("mock.jwt.proof", proof.jwt!!.first())
    }

    @Test
    fun testBuildJwtProofWithJwk() = runTest {
        val mockKey = MockKey()

        val proof = builder.buildJwtProof(
            key = mockKey,
            audience = audience,
            nonce = nonce,
            includeJwk = true
        )

        assertNotNull(proof.jwt)
        assertEquals("mock.jwt.proof", proof.jwt!!.first())
    }
}
