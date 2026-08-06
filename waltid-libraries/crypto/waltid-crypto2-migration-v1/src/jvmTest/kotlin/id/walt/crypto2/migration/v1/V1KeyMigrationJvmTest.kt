package id.walt.crypto2.migration.v1

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.crypto2.serialization.StoredKeyCodec
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** JVM-only coverage for the cryptography provider's private JWK import capability. */
class V1KeyMigrationJvmTest {
    @Test
    fun `private local JWK migrates offline and survives restart`() = runTest {
        val provider = CryptographySoftwareKeyProvider()
        val generated = provider.generate(
            GenerateSoftwareKeyRequest(
                id = KeyId("source"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
        val jwk = assertIs<EncodedKey.Jwk>(generated.storedKey.material)
        val source = """{"type":"jwk","jwk":${jwk.data.toByteArray().decodeToString()},"_keyId":"legacy-kid"}"""
        val migrated = assertIs<StoredKey.Software>(
            V1KeyMigration().migrate(
                KeyId("database-id"),
                source,
                setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
        assertEquals(KeyId("database-id"), migrated.id)
        assertEquals(KeySpec.Ec(EcCurve.P256), migrated.spec)
        assertTrue(assertIs<EncodedKey.Jwk>(migrated.material).privateMaterial)
        // v1 getKeyId() returned _keyId when present, so that value is the `kid` in every credential, DID document
        // and JWKS entry issued before the migration. It must survive, or those references stop resolving.
        assertEquals("legacy-kid", migrated.legacyKeyId())
        assertEquals("legacy-kid", migrated.metadata[V1_LEGACY_KEY_ID_METADATA_KEY])

        val restored = CryptoRuntime(listOf(provider)).restore(
            StoredKeyCodec.decodeFromByteArray(StoredKeyCodec.encodeToByteArray(migrated))
        )
        val algorithm = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256)
        val signature = assertNotNull(restored.capabilities.signer).sign(byteArrayOf(1), algorithm)
        assertTrue(assertNotNull(restored.capabilities.verifier).verify(byteArrayOf(1), signature, algorithm))
    }
}
