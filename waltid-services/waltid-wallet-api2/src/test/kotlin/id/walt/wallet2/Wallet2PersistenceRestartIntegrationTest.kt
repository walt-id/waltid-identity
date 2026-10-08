package id.walt.wallet2

import id.walt.credentials.formats.MdocsCredential
import id.walt.wallet2.data.StoredCredential
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
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
import id.walt.wallet2.data.WalletDescriptor
import id.walt.wallet2.stores.WalletStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.io.path.createTempFile
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
    fun `wallet resolution holds one generation across descriptor and adapter reads`() = runTest {
        val file = createTempFile("wallet-resolution-", ".db").toFile().apply { deleteOnExit() }
        val db = initWallet2Database(Wallet2PersistenceConfig(
            jdbcUrl = "jdbc:sqlite:${file.absolutePath}", maximumPoolSize = 2, minimumIdle = 2))
        try {
            OSSWallet2Service.configurePersistence(db)
            val resolver = OSSWallet2Service.resolver
            val originalStore = OSSWallet2Service.walletStore
            originalStore.saveDescriptor(WalletDescriptor(id = "wallet", staticDid = "did:example:old"))
            val captured = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            OSSWallet2Service.walletStore = object : WalletStore by originalStore {
                override suspend fun loadDescriptor(walletId: String): WalletDescriptor? {
                    val descriptor = originalStore.loadDescriptor(walletId)
                    captured.complete(Unit)
                    release.await()
                    return descriptor
                }
            }
            val resolving = async(Dispatchers.IO) { resolver.resolveWallet("wallet") }
            captured.await()
            val deleting = CompletableDeferred<Unit>()
            val recreation = async(Dispatchers.IO) {
                deleting.complete(Unit)
                originalStore.deleteWallet("wallet")
                originalStore.saveDescriptor(WalletDescriptor(id = "wallet", staticDid = "did:example:new"))
            }
            deleting.await()
            try {
                assertNull(withContext(Dispatchers.IO) { withTimeoutOrNull(250) { recreation.await(); true } })
            } finally { release.complete(Unit) }
            assertEquals("did:example:old", assertNotNull(resolving.await()).staticDid)
            recreation.await()
            assertEquals("did:example:new", assertNotNull(resolver.resolveWallet("wallet")).staticDid)
        } finally {
            OSSWallet2Service.configureInMemory()
        }
    }

    @Test
    fun `resolver isolates a recreated wallet from stale writers and session state`() = runTest {
        val db = initWallet2Database(
            Wallet2PersistenceConfig(jdbcUrl = "jdbc:sqlite::memory:", maximumPoolSize = 1, minimumIdle = 1)
        )
        try {
            OSSWallet2Service.configurePersistence(db)
            val resolver = OSSWallet2Service.resolver
            val shared = resolver.createCredentialStore("shared-credentials")
            val descriptor = Wallet(id = "recreated", credentialStores = listOf(shared))
            resolver.storeWallet(descriptor)
            resolver.storeWallet(descriptor.copy(id = "other-owner"))
            val stale = assertNotNull(resolver.resolveWallet(descriptor.id))
            val oldState = resolver.resolveIssuanceSessionState(descriptor.id)

            // Another service process deletes the wallet without evicting this process's cache.
            OSSWallet2Service.walletStore.deleteWallet(descriptor.id)
            resolver.storeWallet(descriptor)
            val fresh = assertNotNull(resolver.resolveWallet(descriptor.id))
            assertNotSame(oldState, resolver.resolveIssuanceSessionState(descriptor.id))
            val credential = StoredCredential("fresh", MdocsCredential(
                credentialData = buildJsonObject { }, signed = null, docType = "org.iso.18013.5.1.mDL"))
            assertFailsWith<IllegalStateException> {
                stale.credentialStores.single().addCredential(credential.copy(id = "stale"))
            }
            fresh.credentialStores.single().addCredential(credential)
            assertNull(shared.getCredential("stale"))
            assertNotNull(shared.getCredential("fresh"))

            resolver.deleteWallet(descriptor.id)
            assertNull(resolver.resolveWallet(descriptor.id))
            assertNotNull(resolver.resolveWallet("other-owner"))
            assertNotNull(shared.getCredential("fresh"))
            assertFailsWith<IllegalStateException> {
                fresh.credentialStores.single().addCredential(credential.copy(id = "after-delete"))
            }
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
