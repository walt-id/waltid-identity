package id.walt.wallet2

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.KeyUsage
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.persistence.ExposedCredentialStore
import id.walt.wallet2.persistence.ExposedDidStore
import id.walt.wallet2.persistence.ExposedKeyStore
import id.walt.wallet2.persistence.Wallet2PersistenceConfig
import id.walt.wallet2.persistence.initWallet2Database
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Wallet2PersistenceRestartIntegrationTest {
    @Test
    fun `configured persistence restores static key through crypto2 after state reset`() = runTest {
        val db = initWallet2Database(
            Wallet2PersistenceConfig(
                jdbcUrl = "jdbc:sqlite::memory:",
                maximumPoolSize = 1,
                minimumIdle = 1,
            )
        )
        try {
            OSSWallet2Service.configurePersistence(db)
            val staticKey = JWKKey.generate(KeyType.Ed25519)
            OSSWallet2Service.resolver.storeWallet(Wallet(id = "static-restart", staticKey = staticKey))

            OSSWallet2Service.configurePersistence(db)
            val restored = assertNotNull(OSSWallet2Service.resolver.resolveWallet("static-restart"))
            assertTrue(restored.keyStores.isEmpty())
            assertEquals(staticKey.getKeyId(), restored.staticKey?.getKeyId())
            val crypto2Key = assertNotNull(restored.defaultCrypto2Key(setOf(KeyUsage.SIGN)))
            val payload = "restart".encodeToByteArray()
            val signature = assertNotNull(crypto2Key.capabilities.signer).sign(payload, SignatureAlgorithm.EdDsa)

            assertTrue(assertNotNull(crypto2Key.capabilities.verifier).verify(payload, signature, SignatureAlgorithm.EdDsa))
        } finally {
            OSSWallet2Service.configureInMemory()
        }
    }

    @Test
    fun `configured persistence restores wallet stores and signing key after state reset`() = runTest {
        val db = initWallet2Database(
            Wallet2PersistenceConfig(
                jdbcUrl = "jdbc:sqlite::memory:",
                maximumPoolSize = 1,
                minimumIdle = 1,
            )
        )
        try {
            OSSWallet2Service.configurePersistence(db)
            val keyStore = OSSWallet2Service.resolver.createKeyStore("restart-keys")
            val credentialStore = OSSWallet2Service.resolver.createCredentialStore("restart-credentials")
            val didStore = OSSWallet2Service.resolver.createDidStore("restart-dids")
            val keyId = keyStore.addKey(JWKKey.generate(KeyType.Ed25519))
            OSSWallet2Service.resolver.storeWallet(
                Wallet(
                    id = "restart-wallet",
                    keyStores = listOf(keyStore),
                    credentialStores = listOf(credentialStore),
                    didStore = didStore,
                )
            )
            OSSWallet2Service.resolver.linkWalletToAccount("restart-account", "restart-wallet")

            // Recreate all process-local service/store state while keeping the configured database.
            OSSWallet2Service.configurePersistence(db)
            val restored = assertNotNull(OSSWallet2Service.resolver.resolveWallet("restart-wallet"))
            assertIs<ExposedKeyStore>(restored.keyStores.single())
            assertIs<ExposedCredentialStore>(restored.credentialStores.single())
            assertIs<ExposedDidStore>(restored.didStore)
            assertEquals(keyId, restored.listAllKeys().single().keyId)
            assertEquals(listOf("restart-wallet"), OSSWallet2Service.resolver.getWalletIdsForAccount("restart-account"))
            val restoredKey = assertNotNull(restored.findKey(keyId))
            val signed = restoredKey.signJws("{}".encodeToByteArray())
            assertTrue(restoredKey.getPublicKey().verifyJws(signed).isSuccess)

            OSSWallet2Service.resolver.deleteWallet("restart-wallet")
            assertNull(OSSWallet2Service.resolver.resolveWallet("restart-wallet"))
            assertNull(OSSWallet2Service.resolver.resolveKeyStore("restart-keys"))
            assertNull(OSSWallet2Service.resolver.resolveCredentialStore("restart-credentials"))
            assertNull(OSSWallet2Service.resolver.resolveDidStore("restart-dids"))
            assertEquals(emptyList(), OSSWallet2Service.resolver.getWalletIdsForAccount("restart-account"))
        } finally {
            OSSWallet2Service.configureInMemory()
        }
    }
}
