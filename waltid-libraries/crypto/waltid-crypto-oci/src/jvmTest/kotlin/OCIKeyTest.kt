import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.oci.OCIKey
import id.walt.crypto.keys.oci.OCIsdkMetadata
import id.walt.crypto.keys.jwk.JWKKey
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for [OCIKey] that don't require live OCI connectivity.
 * Uses pre-loaded JWK public keys to avoid triggering the lazy OCI client init.
 */
class OCIKeyTest {

    private val p256PublicJwk = """
        {
          "kty": "EC",
          "crv": "P-256",
          "x": "f83OJ3D2xF1Bg8vub9tLe1gHMzV76e8Tus9uPHvRVEU",
          "y": "x_FEzRu9m36HLN_tue659LNpXW6pCyStikYjKIWI5a0",
          "kid": "test-key-1"
        }
    """.trimIndent()

    private val p256KeyId = "ocid1.key.oc1.eu-frankfurt-1.example.abcdef"
    private val testConfig = OCIsdkMetadata(
        vaultId = "ocid1.vault.oc1.eu-frankfurt-1.example",
        compartmentId = "ocid1.compartment.oc1..example",
    )

    @Test
    fun `hasPrivateKey is always false for OCIKey`() = runTest {
        val key = buildOciKeyWithPublicJwk(p256PublicJwk, KeyType.secp256r1)
        assertFalse(key.hasPrivateKey)
    }

    @Test
    fun `toString contains vault id and key type`() = runTest {
        val key = buildOciKeyWithPublicJwk(p256PublicJwk, KeyType.secp256r1)
        val str = key.toString()
        assertTrue(str.contains(testConfig.vaultId), "toString should contain vaultId, got: $str")
        assertTrue(str.contains("secp256r1"), "toString should contain key type, got: $str")
    }

    @Test
    fun `exportJWKObject returns the pre-loaded public JWK`() = runTest {
        val key = buildOciKeyWithPublicJwk(p256PublicJwk, KeyType.secp256r1)
        val obj = key.exportJWKObject()
        assertIs<kotlinx.serialization.json.JsonObject>(obj)
        assertEquals("EC", obj["kty"]?.toString()?.trim('"'))
        assertEquals("P-256", obj["crv"]?.toString()?.trim('"'))
    }

    @Test
    fun `getPublicKey resolves from the pre-loaded JWK without OCI call`() = runTest {
        val key = buildOciKeyWithPublicJwk(p256PublicJwk, KeyType.secp256r1)
        val pub = key.getPublicKey()
        assertFalse(pub.hasPrivateKey)
    }

    @Test
    fun `getKeyId delegates to the cached public key`() = runTest {
        val key = buildOciKeyWithPublicJwk(p256PublicJwk, KeyType.secp256r1)
        assertTrue(key.getKeyId().isNotBlank())
    }

    @Test
    fun `getThumbprint delegates to the cached public key`() = runTest {
        val key = buildOciKeyWithPublicJwk(p256PublicJwk, KeyType.secp256r1)
        assertTrue(key.getThumbprint().isNotBlank())
    }

    @Test
    fun `getPublicKeyRepresentation returns non-empty byte array`() = runTest {
        val key = buildOciKeyWithPublicJwk(p256PublicJwk, KeyType.secp256r1)
        assertTrue(key.getPublicKeyRepresentation().isNotEmpty())
    }

    @Test
    fun `exportJWK throws NotImplementedError`() = runTest {
        val key = buildOciKeyWithPublicJwk(p256PublicJwk, KeyType.secp256r1)
        val ex = runCatching { key.exportJWK() }.exceptionOrNull()
        assertIs<NotImplementedError>(ex)
    }

    @Test
    fun `exportPEM throws NotImplementedError`() = runTest {
        val key = buildOciKeyWithPublicJwk(p256PublicJwk, KeyType.secp256r1)
        val ex = runCatching { key.exportPEM() }.exceptionOrNull()
        assertIs<NotImplementedError>(ex)
    }

    @Test
    fun `verifyJws rejects JWS with wrong algorithm`() = runTest {
        val edKey = JWKKey.generate(KeyType.Ed25519)
        val edJws = edKey.signJws("""{"test": true}""".encodeToByteArray())
        val ociKey = buildOciKeyWithPublicJwk(p256PublicJwk, KeyType.secp256r1)
        val result = runCatching { ociKey.verifyJws(edJws) }
        assertTrue(result.isFailure)
    }

    @Test
    fun `verifyJws rejects malformed JWS with wrong part count`() = runTest {
        val key = buildOciKeyWithPublicJwk(p256PublicJwk, KeyType.secp256r1)
        val result = runCatching { key.verifyJws("only.two.dots.here.extra") }
        assertTrue(result.isFailure)
    }

    @Test
    fun `verifyJws rejects JWS with only two parts`() = runTest {
        val key = buildOciKeyWithPublicJwk(p256PublicJwk, KeyType.secp256r1)
        val result = runCatching { key.verifyJws("header.payload") }
        assertTrue(result.isFailure)
    }

    @Test
    fun `DEFAULT_KEY_LENGTH is 32`() {
        assertEquals(32, OCIKey.DEFAULT_KEY_LENGTH)
    }

    private fun buildOciKeyWithPublicJwk(publicJwk: String, keyType: KeyType): OCIKey {
        val normalisedJwk = Json.encodeToString(Json.parseToJsonElement(publicJwk))
        return OCIKey(
            id = p256KeyId,
            config = testConfig,
            _publicKey = normalisedJwk,
            _keyType = keyType,
        )
    }
}
