import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.OciKeyMeta
import id.walt.crypto.keys.oci.OCIKey
import id.walt.crypto.keys.oci.OCIsdkMetadata
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for [OCIKey]. Require a live OCI environment.
 *
 * Set environment variables to enable:
 *   OCI_VAULT_ID, OCI_COMPARTMENT_ID, OCI_AUTH_TYPE (optional),
 *   OCI_CONFIG_PATH (optional), OCI_CONFIG_PROFILE (optional)
 */
@DisplayName("OCIKey SDK Integration Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OCISdkKeyTest {

    private val vaultId = System.getenv("OCI_VAULT_ID") ?: ""
    private val compartmentId = System.getenv("OCI_COMPARTMENT_ID") ?: ""
    private val authType = System.getenv("OCI_AUTH_TYPE") ?: "INSTANCE_PRINCIPAL"
    private val configPath = System.getenv("OCI_CONFIG_PATH")
    private val configProfile = System.getenv("OCI_CONFIG_PROFILE")

    @BeforeAll
    fun requireOciEnvironment() {
        assumeTrue(
            vaultId.isNotBlank() && compartmentId.isNotBlank(),
            "Skipping: OCI_VAULT_ID and OCI_COMPARTMENT_ID must be set to run these tests"
        )
    }

    private fun config() = OCIsdkMetadata(
        vaultId = vaultId,
        compartmentId = compartmentId,
        authType = authType,
        configFilePath = configPath,
        configProfile = configProfile,
    )

    @Nested
    @DisplayName("Key generation")
    inner class KeyGeneration {

        @Test
        fun `generateKey with default type produces secp256r1 key`() = runTest {
            val key = OCIKey.generateKey(config())
            assertEquals(KeyType.secp256r1, key.keyType)
        }

        @ParameterizedTest(name = "keyType={0}")
        @MethodSource("OCISdkKeyTest#supportedKeyTypes")
        fun `generateKey produces a key with the requested type`(keyType: KeyType) = runTest {
            val key = OCIKey.generateKey(keyType, config())
            assertEquals(keyType, key.keyType)
        }

        @Test
        fun `generated key always reports hasPrivateKey = false`() = runTest {
            val key = OCIKey.generateKey(config())
            assertFalse(key.hasPrivateKey)
        }

        @Test
        fun `generated key has a non-blank OCID id`() = runTest {
            val key = OCIKey.generateKey(config())
            assertTrue(key.id.startsWith("ocid1.key."), "Key ID should be an OCID: ${key.id}")
        }

        @Test
        fun `toString contains vaultId`() = runTest {
            val key = OCIKey.generateKey(config())
            assertTrue(key.toString().contains(vaultId))
        }
    }

    @Nested
    @DisplayName("Public key retrieval")
    inner class PublicKeyRetrieval {

        @Test
        fun `getPublicKey returns a key without a private key`() = runTest {
            val key = OCIKey.generateKey(config())
            assertFalse(key.getPublicKey().hasPrivateKey)
        }

        @Test
        fun `getPublicKey is consistent across multiple calls`() = runTest {
            val key = OCIKey.generateKey(config())
            assertEquals(key.getPublicKey().getKeyId(), key.getPublicKey().getKeyId())
        }

        @Test
        fun `getPublicKeyRepresentation returns non-empty byte array`() = runTest {
            val key = OCIKey.generateKey(config())
            assertTrue(key.getPublicKeyRepresentation().isNotEmpty())
        }

        @Test
        fun `exportJWKObject returns a valid JWK JsonObject`() = runTest {
            val key = OCIKey.generateKey(config())
            val jwkObj = key.exportJWKObject()
            assertIs<JsonObject>(jwkObj)
            assertTrue(jwkObj.isNotEmpty())
            assertNotNull(jwkObj["kty"], "JWK must contain 'kty'")
        }
    }

    @Nested
    @DisplayName("Key identity")
    inner class KeyIdentity {

        @Test
        fun `getKeyId returns a non-blank string`() = runTest {
            val key = OCIKey.generateKey(config())
            assertTrue(key.getKeyId().isNotBlank())
        }

        @Test
        fun `getThumbprint returns a non-blank string`() = runTest {
            val key = OCIKey.generateKey(config())
            assertTrue(key.getThumbprint().isNotBlank())
        }

        @Test
        fun `getMeta returns correct keyId and a non-blank keyVersion`() = runTest {
            val key = OCIKey.generateKey(config())
            val meta = key.getMeta()
            assertIs<OciKeyMeta>(meta)
            assertEquals(key.id, meta.keyId)
            assertTrue(meta.keyVersion.isNotBlank())
        }
    }

    @Nested
    @DisplayName("Raw sign + verify")
    inner class RawSignVerify {

        @ParameterizedTest(name = "keyType={0}")
        @MethodSource("OCISdkKeyTest#supportedKeyTypes")
        fun `signRaw and verifyRaw succeed for all supported key types`(keyType: KeyType) = runTest {
            val key = OCIKey.generateKey(keyType, config())
            val plaintext = "payload for $keyType".toByteArray()
            val signed = key.signRaw(plaintext)
            val result = key.verifyRaw(signed, plaintext)
            assertTrue(result.isSuccess, "verifyRaw failed for $keyType: ${result.exceptionOrNull()}")
        }

        @Test
        fun `verifyRaw returns plaintext on success`() = runTest {
            val key = OCIKey.generateKey(config())
            val plaintext = "test payload".toByteArray()
            val signed = key.signRaw(plaintext)
            val result = key.verifyRaw(signed, plaintext)
            assertTrue(result.isSuccess)
            assertTrue(plaintext.contentEquals(result.getOrThrow()))
        }

        @Test
        fun `verifyRaw fails for tampered signature`() = runTest {
            val key = OCIKey.generateKey(config())
            val plaintext = "test payload".toByteArray()
            val signed = key.signRaw(plaintext)
            val tampered = signed.copyOf().also { it[it.size / 2] = it[it.size / 2].inc() }
            val result = key.verifyRaw(tampered, plaintext)
            assertTrue(result.isFailure, "Verification should fail for tampered signature")
        }

        @Test
        fun `verifyRaw with null plaintext throws`() = runTest {
            val key = OCIKey.generateKey(config())
            val signed = key.signRaw("data".toByteArray())
            assertFailsWith<IllegalArgumentException> {
                key.verifyRaw(signed, null)
            }
        }
    }

    @Nested
    @DisplayName("JWS sign + verify")
    inner class JwsSignVerify {

        @ParameterizedTest(name = "keyType={0}")
        @MethodSource("OCISdkKeyTest#supportedKeyTypes")
        fun `signJws and verifyJws succeed for all supported key types`(keyType: KeyType) = runTest {
            val key = OCIKey.generateKey(keyType, config())
            val payload = """{"sub":"test","iss":"oci-sdk-test","keyType":"$keyType"}"""
            val signed = key.signJws(payload.encodeToByteArray())
            val result = key.verifyJws(signed)
            assertTrue(result.isSuccess, "verifyJws failed for $keyType: ${result.exceptionOrNull()}")
        }

        @Test
        fun `signJws produces a 3-part dot-separated string`() = runTest {
            val key = OCIKey.generateKey(config())
            val signed = key.signJws("""{"hello":"world"}""".encodeToByteArray())
            assertEquals(3, signed.split(".").size)
        }

        @Test
        fun `verifyJws returns the original payload as JsonElement`() = runTest {
            val key = OCIKey.generateKey(config())
            val payload = """{"sub":"user123","claim":"value"}"""
            val signed = key.signJws(payload.encodeToByteArray())
            val result = key.verifyJws(signed)
            assertTrue(result.isSuccess)
            assertIs<kotlinx.serialization.json.JsonElement>(result.getOrThrow())
        }

        @Test
        fun `signJws with custom headers includes them in JWS header`() = runTest {
            val key = OCIKey.generateKey(config())
            val keyId = key.getKeyId()
            val signed = key.signJws("""{"test":true}""".encodeToByteArray(), mapOf("kid" to JsonPrimitive(keyId)))
            val headerJson = signed.split(".")[0]
                .let { java.util.Base64.getUrlDecoder().decode(it).decodeToString() }
            assertTrue(headerJson.contains(keyId), "Header should contain kid: $headerJson")
        }

        @Test
        fun `verifyJws fails for tampered payload`() = runTest {
            val key = OCIKey.generateKey(config())
            val signed = key.signJws("""{"legit":true}""".encodeToByteArray())
            val parts = signed.split(".")
            val tamperedPayload = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("""{"tampered":true}""".encodeToByteArray())
            val tampered = "${parts[0]}.$tamperedPayload.${parts[2]}"
            val result = key.verifyJws(tampered)
            assertTrue(result.isFailure)
        }

        @Test
        fun `verifyJws with wrong part count returns failure`() = runTest {
            val key = OCIKey.generateKey(config())
            val result = runCatching { key.verifyJws("only.two") }
            assertTrue(result.isFailure)
        }
    }

    @Nested
    @DisplayName("Export restrictions")
    inner class ExportRestrictions {

        @Test
        fun `exportJWK throws NotImplementedError`() = runTest {
            val key = OCIKey.generateKey(config())
            assertFailsWith<NotImplementedError> { key.exportJWK() }
        }

        @Test
        fun `exportPEM throws NotImplementedError`() = runTest {
            val key = OCIKey.generateKey(config())
            assertFailsWith<NotImplementedError> { key.exportPEM() }
        }
    }

    @Nested
    @DisplayName("Key deletion")
    inner class KeyDeletion {

        @Test
        fun `deleteKey schedules deletion and returns true`() = runTest {
            val key = OCIKey.generateKey(config())
            assertTrue(key.deleteKey())
        }
    }

    @Nested
    @DisplayName("Unsupported key types")
    inner class UnsupportedKeyTypes {

        @Test
        fun `generateKey with Ed25519 throws IllegalArgumentException`() = runTest {
            assertFailsWith<IllegalArgumentException> {
                OCIKey.generateKey(KeyType.Ed25519, config())
            }
        }

        @Test
        fun `generateKey with secp256k1 throws IllegalArgumentException`() = runTest {
            assertFailsWith<IllegalArgumentException> {
                OCIKey.generateKey(KeyType.secp256k1, config())
            }
        }
    }

    companion object {
        @JvmStatic
        fun supportedKeyTypes(): Stream<Arguments> = Stream.of(
            Arguments.of(KeyType.secp256r1),
            Arguments.of(KeyType.secp384r1),
            Arguments.of(KeyType.secp521r1),
            Arguments.of(KeyType.RSA),
            Arguments.of(KeyType.RSA3072),
            Arguments.of(KeyType.RSA4096),
        )
    }
}
