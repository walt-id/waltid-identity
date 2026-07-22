package id.walt.wallet2.persistence.keys

import id.walt.crypto.keys.KeyType
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.ManagedKey
import id.walt.crypto2.keys.ProviderId
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.crypto2.serialization.BinaryData
import id.walt.crypto2.signum.SignumKeyPolicy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MobileStoredKeyMigrationTest {
    @Test
    fun `software JWK migrates and restores after restart`() = runTest {
        val runtime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))
        val source = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("software-key"),
                spec = KeyType.Ed25519.toCrypto2KeySpec(),
                usages = KEY_USAGES,
            )
        ).storedKey
        val migration = MobileStoredKeyMigration(null)

        val stored = assertIs<StoredKey.Software>(
            migration.migrate(
                id = source.id,
                keyType = KeyType.Ed25519,
                platformBacked = false,
                keyMaterial = assertIs<EncodedKey.Jwk>(source.material).data.toByteArray().decodeToString(),
                usages = KEY_USAGES,
            )
        )

        assertNotNull(MobileStoredKeyMigration(null).restore(stored).capabilities.signer)
    }

    @Test
    fun `unsupported secp256k1 software key retains v1 fallback`() = runTest {
        assertNull(
            MobileStoredKeyMigration(null).migrate(
                id = KeyId("secp256k1-key"),
                keyType = KeyType.secp256k1,
                platformBacked = false,
                keyMaterial = "{}",
                usages = KEY_USAGES,
            )
        )
    }

    @Test
    fun `platform key migration delegates alias adoption and restoration`() = runTest {
        val provider = FakePlatformProvider()
        val stored = assertIs<StoredKey.Managed>(
            MobileStoredKeyMigration(provider).migrate(
                id = KeyId("platform-key"),
                keyType = KeyType.secp256r1,
                platformBacked = true,
                keyMaterial = null,
                usages = KEY_USAGES,
            )
        )

        assertEquals("platform-key", provider.migratedId?.value)
        assertTrue(provider.restoreCount > 0)
        assertEquals(ProviderId("fake-platform"), stored.provider)
    }

    @Test
    fun `platform key without crypto2 provider retains v1 fallback`() = runTest {
        assertNull(
            MobileStoredKeyMigration(null).migrate(
                id = KeyId("platform-key"),
                keyType = KeyType.secp256r1,
                platformBacked = true,
                keyMaterial = null,
                usages = KEY_USAGES,
            )
        )
    }

    private class FakePlatformProvider : Crypto2PlatformKeyProvider {
        var migratedId: KeyId? = null
        var restoreCount = 0

        override suspend fun generateManagedKey(
            id: KeyId,
            spec: KeySpec,
            usages: Set<KeyUsage>,
            policy: SignumKeyPolicy?,
        ): ManagedKey = error("Not used by migration tests")

        override suspend fun migratePlatformKey(
            id: KeyId,
            keyType: KeyType,
            usages: Set<KeyUsage>,
        ): StoredKey.Managed {
            migratedId = id
            return StoredKey.Managed(
                version = StoredKey.CURRENT_VERSION,
                id = id,
                spec = keyType.toCrypto2KeySpec(),
                usages = usages,
                provider = ProviderId("fake-platform"),
                providerSchemaVersion = 1,
                providerData = BinaryData("platform-key".encodeToByteArray()),
            )
        }

        override suspend fun restoreManagedKey(stored: StoredKey.Managed): Key {
            restoreCount++
            return CryptoRuntime(listOf(CryptographySoftwareKeyProvider())).generateSoftwareKey(
                GenerateSoftwareKeyRequest(
                    id = stored.id,
                    spec = stored.spec,
                    usages = stored.usages,
                )
            )
        }

        override suspend fun deleteManagedKey(stored: StoredKey.Managed) = Unit
    }

    private companion object {
        val KEY_USAGES = setOf(KeyUsage.SIGN, KeyUsage.VERIFY)
    }
}
