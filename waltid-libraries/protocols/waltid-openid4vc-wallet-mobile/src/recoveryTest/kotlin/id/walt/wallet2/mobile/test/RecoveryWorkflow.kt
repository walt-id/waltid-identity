@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.wallet2.mobile.test

import app.cash.sqldelight.db.SqlDriver
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.crypto2.serialization.BinaryData
import id.walt.openid4vp.clientidprefix.ClientIdTrustConfiguration
import id.walt.wallet2.mobile.MobileWallet
import id.walt.wallet2.mobile.MobileWalletConfig
import id.walt.wallet2.mobile.createEncryptedSqlDelightMobileWallet
import id.walt.wallet2.mobile.identity.*
import id.walt.wallet2.persistence.db.WalletPersistenceDatabase
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKey
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKeyProvider
import id.walt.wallet2.persistence.keys.KeyUseAuthorizationPolicy
import id.walt.wallet2.persistence.keys.PlatformManagedKeyProvider
import id.walt.wallet2.persistence.keys.PlatformManagedKeyRestoration
import id.walt.wallet2.persistence.stores.SqlDelightKeyStore
import kotlin.test.*
import kotlin.uuid.Uuid

/** Shared Android/iOS qualification phases. Only public metadata may leave the application. */
internal class RecoveryWorkflow(
    private val provider: IdentityRecoveryProvider,
    private val open: suspend () -> RecoveryTestWallet,
) {
    suspend fun prepare(storage: IdentityKeyStorage): WalletIdentity = session { test ->
        assertIs<RecoveryAvailability.Available>(provider.availability(), "Recovery provider prerequisite missing")
        assertTrue(provider.list().isEmpty(), "Use a fresh test namespace")
        assertEquals(WalletIdentityState.Absent, test.wallet.identities.state())
        val options = assertIs<IdentityOptions.Available>(test.wallet.identities.creationOptions(IdentityIntent.Recoverable))
        val option = (listOf(options.recommended) + options.alternatives).single { it.storage == storage }
        val identity = assertIs<IdentityOperationResult.Active>(test.wallet.identities.create(option)).identity
        assertEquals(RecoveryReceipt.AcceptedLocally, assertIs<IdentityRecoveryState.Submitted>(identity.recovery).receipt)
        test.verify(identity)
        identity
    }

    suspend fun loseLocalState(expected: WalletIdentity) = session { test ->
        test.verify(expected)
        test.deleteLocalState(expected)
        // Deleting local wallet state must preserve the independently stored recovery record.
        assertNotNull(provider.retrieve(expected.id))
    }

    suspend fun restore(expected: WalletIdentity, storage: IdentityKeyStorage) = session { test ->
        assertEquals(WalletIdentityState.Absent, test.wallet.identities.state())
        assertNull(test.keys.getCrypto2Key(expected.keyId, setOf(KeyUsage.SIGN)))
        val candidate = test.wallet.identities.discoverRecovery().candidates.single { it.reference.recordId == expected.id }
        val options = test.wallet.identities.restorationOptions(candidate)
        if (expected.storage == IdentityKeyStorage.NativeStorage) {
            assertTrue(options.none { it.storage == IdentityKeyStorage.EncryptedDatabase },
                "Native-storage recovery must not offer a weaker database-storage destination")
        }
        val option = options.single { it.storage == storage }
        val restored = assertIs<IdentityOperationResult.Active>(test.wallet.identities.restore(option)).identity
        assertIs<IdentityRecoveryState.Recovered>(restored.recovery)
        test.verify(expected, storage)
    }

    suspend fun verify(expected: WalletIdentity, storage: IdentityKeyStorage) = session { it.verify(expected, storage) }

    suspend fun cleanup() {
        session { it.wallet.deleteWallet() }
        // This provider is scoped to one disposable run, never to a personal wallet namespace.
        provider.list().forEach { provider.delete(it) }
        assertTrue(provider.list().isEmpty())
    }

    private suspend fun <T> session(block: suspend (RecoveryTestWallet) -> T): T {
        val test = open()
        try { return block(test) } finally { test.close() }
    }
}

/** Uses production encrypted persistence wiring while retaining a test-only handle for signing proof. */
internal class RecoveryTestWallet private constructor(
    val wallet: MobileWallet,
    val keys: SqlDelightKeyStore,
    private val driver: SqlDriver,
    private val nativeKeys: PlatformManagedKeyProvider,
) {
    fun close() = driver.close()

    suspend fun deleteLocalState(expected: WalletIdentity) {
        val key = assertIs<StorableKey>(keys.getCrypto2Key(expected.keyId, setOf(KeyUsage.SIGN)))
        val managed = key.storedKey as? StoredKey.Managed
        wallet.deleteWallet()
        if (managed != null) assertIs<PlatformManagedKeyRestoration.Missing>(nativeKeys.restoreManagedKey(managed))
    }

    suspend fun verify(expected: WalletIdentity, storage: IdentityKeyStorage = expected.storage) {
        val actual = assertIs<WalletIdentityState.Active>(wallet.identities.state()).identity
        assertEquals(expected.id, actual.id)
        assertEquals(expected.keyId, actual.keyId)
        assertEquals(expected.did, actual.did)
        assertEquals(expected.publicJwk, actual.publicJwk)
        assertEquals(storage, actual.storage)
        assertEquals(expected.authorization, actual.authorization)
        assertEquals(KeyOrigin.IMPORTED, actual.keyFacts.origin)
        val key = assertIs<StorableKey>(keys.getCrypto2Key(expected.keyId, setOf(KeyUsage.SIGN)))
        if (storage == IdentityKeyStorage.EncryptedDatabase) {
            assertIs<StoredKey.Software>(key.storedKey)
            assertEquals(KeySecurityLevel.SOFTWARE, actual.keyFacts.securityLevel)
            assertEquals(KeyProtectionLevel.SOFTWARE, actual.keyFacts.protection)
        } else {
            val managed = assertIs<StoredKey.Managed>(key.storedKey)
            assertEquals(nativeKeys.keyFacts(managed), actual.keyFacts)
            if (storage == IdentityKeyStorage.Hardware) assertEquals(KeyProtectionLevel.HARDWARE, actual.keyFacts.protection)
        }
        if (storage == expected.storage) assertEquals(expected.keyFacts, actual.keyFacts)
        val public = CryptoRuntime(defaultSoftwareKeyProviders()).restore(StoredKey.Software(
            StoredKey.CURRENT_VERSION, KeyId("recovery-proof"), key.spec, setOf(KeyUsage.VERIFY),
            EncodedKey.Jwk(BinaryData(expected.publicJwk.encodeToByteArray()), false)))
        val challenge = Uuid.random().toString().encodeToByteArray()
        val algorithm = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256)
        val signature = assertNotNull(key.capabilities.signer).sign(challenge, algorithm)
        assertTrue(assertNotNull(public.capabilities.verifier).verify(challenge, signature, algorithm))
        assertFalse(assertNotNull(public.capabilities.verifier).verify(challenge + byteArrayOf(1), signature, algorithm))
    }

    companion object {
        suspend fun open(
            runId: String,
            provider: IdentityRecoveryProvider,
            databaseKeys: DatabaseEncryptionKeyProvider,
            nativeKeys: PlatformManagedKeyProvider,
            openDriver: (String, DatabaseEncryptionKey, Boolean, String) -> SqlDriver,
            deleteDatabase: (String) -> Unit,
        ): RecoveryTestWallet {
            require(runId.matches(Regex("[a-f0-9-]{36}")))
            val config = MobileWalletConfig(walletId = "wal749-recovery-$runId", identity = IdentityConfiguration(
                recoveryProviders = listOf(provider), authorization = IdentityAuthorization.Explicit(KeyUseAuthorizationPolicy.None)))
            lateinit var driver: SqlDriver
            val wallet = createEncryptedSqlDelightMobileWallet(config, ClientIdTrustConfiguration(), databaseKeys, nativeKeys,
                openEncryptedDriver = { name, key, local, walletId -> openDriver(name, key, local, walletId).also { driver = it } },
                deleteDatabase = deleteDatabase)
            return RecoveryTestWallet(wallet, SqlDelightKeyStore(nativeKeys, WalletPersistenceDatabase(driver).walletPersistenceQueries,
                config.walletId), driver, nativeKeys)
        }
    }
}
