@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.wallet2.mobile.identity

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import id.walt.crypto2.keys.ManagedKey
import id.walt.crypto2.keys.StoredKey
import id.walt.did.dids.Crypto2DidService
import id.walt.openid4vp.clientidprefix.ClientIdTrustConfiguration
import id.walt.wallet2.mobile.MobileWalletConfig
import id.walt.wallet2.mobile.createSqlDelightMobileWallet
import id.walt.wallet2.persistence.db.WalletPersistenceDatabase
import id.walt.wallet2.persistence.keys.*
import id.walt.wallet2.persistence.stores.SqlDelightKeyStore
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class WalletIdentitiesTest {
    @Test fun `default configuration does not expose recoverable or unauthenticated software options`() = runTest {
        Fixture(IdentityConfiguration()).use { fixture ->
            assertIs<IdentityOptions.Unavailable>(fixture.wallet.identities.creationOptions(IdentityIntent.Recoverable))
            assertIs<IdentityOptions.Unavailable>(fixture.wallet.identities.creationOptions())
            assertTrue(fixture.provider.records.isEmpty())
        }
    }

    @Test fun `software creation persists explicit active binding and restarts`() = runTest {
        Fixture().use { fixture ->
            val options = assertIs<IdentityOptions.Available>(fixture.wallet.identities.creationOptions())
            assertEquals(IdentityKeyStorage.EncryptedDatabase, options.recommended.storage)
            val created = assertIs<IdentityOperationResult.Active>(fixture.wallet.identities.create(options.recommended)).identity
            assertTrue(created.did.startsWith("did:jwk:"))
            assertFalse(created.publicJwk.contains("\"d\""))
            assertEquals(created.keyId, fixture.queries.selectActiveIdentity("default").executeAsOne().key_id)
            assertEquals(created, assertIs<WalletIdentityState.Active>(fixture.reopen().identities.state()).identity)
            assertIs<IdentityOperationResult.Failed>(fixture.wallet.identities.create(options.recommended))
        }
    }

    @Test fun `backup failure remains pending across restart and retry activates the original identity`() = runTest {
        Fixture().use { fixture ->
            fixture.provider.failStore = true
            val option = assertIs<IdentityOptions.Available>(fixture.wallet.identities.creationOptions(IdentityIntent.Recoverable)).recommended
            val pending = assertIs<IdentityOperationResult.Pending>(fixture.wallet.identities.create(option))
            assertNull(fixture.queries.selectActiveIdentity("default").executeAsOneOrNull())
            assertNull(SqlDelightKeyStore(NoNative, fixture.queries, "default").getDefaultCrypto2Key())
            val restarted = fixture.reopen()
            assertEquals(pending.identityId, assertIs<WalletIdentityState.Pending>(restarted.identities.state()).identityId)
            fixture.provider.failStore = false
            val active = assertIs<IdentityOperationResult.Active>(restarted.identities.resumePending(pending.identityId))
            assertEquals(pending.identityId, active.identity.id)
            assertEquals(1, fixture.queries.selectAll().executeAsList().size)
            assertEquals(RecoveryReceipt.AcceptedLocally, assertIs<IdentityRecoveryState.Submitted>(active.identity.recovery).receipt)
        }
    }

    @Test fun `recovery preserves exact DID public key and key ID in a fresh database`() = runTest {
        Fixture().use { original ->
            val option = assertIs<IdentityOptions.Available>(original.wallet.identities.creationOptions(IdentityIntent.Recoverable)).recommended
            val expected = assertIs<IdentityOperationResult.Active>(original.wallet.identities.create(option)).identity
            Fixture(provider = original.provider).use { destination ->
                val candidate = destination.wallet.identities.recoveryCandidates().single()
                val restore = destination.wallet.identities.restorationOptions(candidate).single()
                val restored = assertIs<IdentityOperationResult.Active>(destination.wallet.identities.restore(restore)).identity
                assertEquals(expected.did, restored.did)
                assertEquals(expected.publicJwk, restored.publicJwk)
                assertEquals(expected.keyId, restored.keyId)
                assertEquals(expected.id, restored.id)
            }
        }
    }

    @Test fun `options cannot cross wallet instances and provider changes invalidate creation`() = runTest {
        Fixture().use { fixture ->
            val option = assertIs<IdentityOptions.Available>(fixture.wallet.identities.creationOptions(IdentityIntent.Recoverable)).recommended
            assertEquals(IdentityFailure.StaleOption, assertIs<IdentityOperationResult.Failed>(fixture.reopen().identities.create(option)).reason)
            fixture.provider.available = false
            assertEquals(IdentityFailure.StaleOption, assertIs<IdentityOperationResult.Failed>(fixture.wallet.identities.create(option)).reason)
            assertTrue(fixture.queries.selectAll().executeAsList().isEmpty())
        }
    }

    @Test fun `device-bound host policy prevents enabling backup for exportable software keys`() = runTest {
        Fixture(configuration = IdentityConfiguration(authorization = IdentityAuthorization.Explicit(KeyUseAuthorizationPolicy.None),
            policy = IdentityKeyPolicy.DeviceBound)).use { fixture ->
            val option = assertIs<IdentityOptions.Available>(fixture.wallet.identities.creationOptions()).recommended
            val created = assertIs<IdentityOperationResult.Active>(fixture.wallet.identities.create(option)).identity
            assertTrue(fixture.wallet.identities.backupOptions(created.id).isEmpty())
            assertIs<IdentityOptions.Unavailable>(fixture.wallet.identities.creationOptions(IdentityIntent.Recoverable))
        }
    }

    @Test fun `enabling backup for an existing software identity exports its original private key`() = runTest {
        Fixture().use { fixture ->
            val created = assertIs<IdentityOperationResult.Active>(fixture.wallet.identities.create(
                assertIs<IdentityOptions.Available>(fixture.wallet.identities.creationOptions()).recommended)).identity
            val result = assertIs<IdentityOperationResult.Active>(fixture.wallet.identities.backup(
                fixture.wallet.identities.backupOptions(created.id).single()))
            assertEquals(created.did, result.identity.did)
            val record = recordJson.decodeFromString<RecoveryRecord>(fixture.provider.records.values.single().decodeToString())
            assertIs<RecoverySecret.Exported>(record.secret)
            Fixture(provider = fixture.provider).use { destination ->
                val option = destination.wallet.identities.restorationOptions(destination.wallet.identities.recoveryCandidates().single()).single()
                assertEquals(created.publicJwk, assertIs<IdentityOperationResult.Active>(destination.wallet.identities.restore(option)).identity.publicJwk)
            }
        }
    }

    @Test fun `corrupted or changed recovery record cannot create a destination key`() = runTest {
        Fixture().use { original ->
            val created = assertIs<IdentityOperationResult.Active>(original.wallet.identities.create(
                assertIs<IdentityOptions.Available>(original.wallet.identities.creationOptions(IdentityIntent.Recoverable)).recommended)).identity
            Fixture(provider = original.provider).use { destination ->
                val candidate = destination.wallet.identities.recoveryCandidates().single()
                val option = destination.wallet.identities.restorationOptions(candidate).single()
                original.provider.records[created.id] = "{}".encodeToByteArray()
                assertTrue(destination.wallet.identities.restorationOptions(candidate).isEmpty())
                assertEquals(IdentityFailure.StaleOption, assertIs<IdentityOperationResult.Failed>(destination.wallet.identities.restore(option)).reason)
                assertTrue(destination.queries.selectAll().executeAsList().isEmpty())
            }
        }
    }

    @Test fun `local recovery retention and provider deletion have separate effects`() = runTest {
        val provider = MemoryRecovery()
        Fixture(IdentityConfiguration(recoveryProviders = listOf(provider),
            authorization = IdentityAuthorization.Explicit(KeyUseAuthorizationPolicy.None),
            localRecoveryMaterial = LocalRecoveryMaterialRetention.DiscardAfterSubmission), provider).use { fixture ->
            val option = assertIs<IdentityOptions.Available>(fixture.wallet.identities.creationOptions(IdentityIntent.Recoverable)).recommended
            val created = assertIs<IdentityOperationResult.Active>(fixture.wallet.identities.create(option)).identity
            val record = recordJson.decodeFromString<IdentityRecord>(fixture.queries.selectIdentityRecord("default").executeAsOne().payload)
            assertNull(record.recovery)
            assertEquals(1, provider.records.size)
            val originalRecovery = provider.records.getValue(created.id).copyOf()
            assertIs<IdentityOperationResult.Active>(fixture.wallet.identities.backup(
                fixture.wallet.identities.backupOptions(created.id).single()))
            assertContentEquals(originalRecovery, provider.records.getValue(created.id))
            assertEquals(RecoveryReceipt.ConfirmedByProvider, fixture.wallet.identities.deleteRecovery(
                fixture.wallet.identities.recoveryCandidates().single()))
            assertTrue(provider.records.isEmpty())
            val active = assertIs<WalletIdentityState.Active>(fixture.wallet.identities.state()).identity
            assertEquals(created.keyId, active.keyId)
            assertIs<IdentityRecoveryState.RemovalRequested>(active.recovery)
            assertNotNull(SqlDelightKeyStore(NoNative, fixture.queries, "default").getDefaultKeyMaterial())
        }
    }

    @Test fun `missing identity association cannot silently replace an existing key`() = runTest {
        Fixture().use { fixture ->
            val created = assertIs<IdentityOperationResult.Active>(fixture.wallet.identities.initialize()).identity
            fixture.queries.deleteIdentityRecord("default")
            assertEquals(IdentityFailure.KeyUnavailable, assertIs<WalletIdentityState.Unavailable>(fixture.wallet.identities.state()).reason)
            assertIs<IdentityOperationResult.Failed>(fixture.wallet.identities.initialize())
            assertEquals(created.keyId, fixture.queries.selectAll().executeAsList().single().key_id)
        }
    }

    @Test fun `pending default cannot be resolved by issuance key material path`() = runTest {
        Fixture().use { fixture ->
            fixture.provider.failStore = true
            val option = assertIs<IdentityOptions.Available>(fixture.wallet.identities.creationOptions(IdentityIntent.Recoverable)).recommended
            assertIs<IdentityOperationResult.Pending>(fixture.wallet.identities.create(option))
            assertNull(SqlDelightKeyStore(NoNative, fixture.queries, "default").getDefaultKeyMaterial())
        }
    }

    @Test fun `default initialization never selects a weaker authorization alternative`() = runTest {
        Fixture(IdentityConfiguration(alternativeAuthorizations = listOf(KeyUseAuthorizationPolicy.None))).use { fixture ->
            assertIs<IdentityOptions.Available>(fixture.wallet.identities.creationOptions())
            assertEquals(IdentityFailure.UnsupportedPolicy, assertIs<IdentityOperationResult.Failed>(fixture.wallet.identities.initialize()).reason)
            assertTrue(fixture.queries.selectAll().executeAsList().isEmpty())
        }
    }

    @Test fun `cancel pending setup cleans local material and permits a new explicit attempt`() = runTest {
        Fixture().use { fixture ->
            fixture.provider.failStore = true
            val option = assertIs<IdentityOptions.Available>(fixture.wallet.identities.creationOptions(IdentityIntent.Recoverable)).recommended
            val pending = assertIs<IdentityOperationResult.Pending>(fixture.wallet.identities.create(option))
            fixture.wallet.identities.cancelPending(pending.identityId)
            fixture.wallet.identities.cancelPending(pending.identityId)
            assertEquals(WalletIdentityState.Absent, fixture.wallet.identities.state())
            assertTrue(fixture.queries.selectAll().executeAsList().isEmpty())
        }
    }

    @Test fun `wrong record identifier private public metadata and wrong derivation are rejected before native import`() = runTest {
        Fixture().use { source ->
            val identity = assertIs<IdentityOperationResult.Active>(source.wallet.identities.create(
                assertIs<IdentityOptions.Available>(source.wallet.identities.creationOptions(IdentityIntent.Recoverable)).recommended)).identity
            val original = recordJson.decodeFromString<RecoveryRecord>(source.provider.records.getValue(identity.id).decodeToString())
            val derived = assertIs<RecoverySecret.Derived>(original.secret)
            val invalid = listOf(
                original.copy(identityId = "different-record"),
                original.copy(publicJwk = original.publicJwk.dropLast(1) + ",\"d\":\"secret\"}"),
                original.copy(secret = derived.copy(domain = "different-domain")),
                original.copy(format = "unknown-format"),
                original.copy(version = 2),
            )
            Fixture(provider = source.provider).use { destination ->
                val candidate = destination.wallet.identities.recoveryCandidates().single()
                for (record in invalid) {
                    source.provider.records[identity.id] = record.encode()
                    assertTrue(destination.wallet.identities.restorationOptions(candidate).isEmpty())
                    assertTrue(destination.queries.selectAll().executeAsList().isEmpty())
                }
            }
        }
    }

    @Test fun `provider acknowledgement without exact readback never activates an identity`() = runTest {
        Fixture().use { fixture ->
            fixture.provider.corruptReadback = true
            val result = fixture.wallet.identities.create(
                assertIs<IdentityOptions.Available>(fixture.wallet.identities.creationOptions(IdentityIntent.Recoverable)).recommended)
            val pending = assertIs<IdentityOperationResult.Pending>(result)
            assertNull(fixture.queries.selectActiveIdentity("default").executeAsOneOrNull())
            fixture.provider.corruptReadback = false
            assertIs<IdentityOperationResult.Active>(fixture.wallet.identities.resumePending(pending.identityId))
        }
    }

    @Test fun `pending setup must not activate after its local key disappears`() = runTest {
        Fixture().use { f ->
            f.provider.failStore = true
            val option = assertIs<IdentityOptions.Available>(f.wallet.identities.creationOptions(IdentityIntent.Recoverable)).recommended
            val pending = assertIs<IdentityOperationResult.Pending>(f.wallet.identities.create(option))
            val keyId = f.queries.selectAll().executeAsList().single().key_id
            assertTrue(SqlDelightKeyStore(NoNative, f.queries, "default").removeKey(keyId))
            f.provider.failStore = false
            val restarted = f.reopen()
            val result = restarted.identities.resumePending(pending.identityId)
            assertFalse(result is IdentityOperationResult.Active, "Activated missing key: $result; actual state=${restarted.identities.state()}")
        }
    }
    @Test fun `pending setup must respect newly configured device-bound policy before upload`() = runTest {
        Fixture().use { f ->
            f.provider.failStore = true
            val option = assertIs<IdentityOptions.Available>(f.wallet.identities.creationOptions(IdentityIntent.Recoverable)).recommended
            val pending = assertIs<IdentityOperationResult.Pending>(f.wallet.identities.create(option))
            f.provider.failStore = false
            val restarted = f.reopen(IdentityConfiguration(recoveryProviders = listOf(f.provider),
                authorization = IdentityAuthorization.Explicit(KeyUseAuthorizationPolicy.None), policy = IdentityKeyPolicy.DeviceBound))
            restarted.identities.resumePending(pending.identityId)
            assertTrue(f.provider.records.isEmpty(), "Uploaded a secret after the host prohibited backup")
        }
    }
    @Test fun `required provider confirmation survives restart and retains recovery until confirmed`() = runTest {
        val provider = MemoryRecovery()
        val configuration = IdentityConfiguration(recoveryProviders = listOf(provider),
            authorization = IdentityAuthorization.Explicit(KeyUseAuthorizationPolicy.None),
            recoveryConfirmation = RecoveryConfirmation.ProviderConfirmation,
            localRecoveryMaterial = LocalRecoveryMaterialRetention.DiscardAfterSubmission)
        Fixture(configuration, provider).use { fixture ->
            val option = assertIs<IdentityOptions.Available>(fixture.wallet.identities.creationOptions(IdentityIntent.Recoverable)).recommended
            val pending = assertIs<IdentityOperationResult.Pending>(fixture.wallet.identities.create(option))
            assertNull(fixture.queries.selectActiveIdentity("default").executeAsOneOrNull())
            val restarted = fixture.reopen(configuration.copy(recoveryConfirmation = RecoveryConfirmation.LocalAcceptance))
            assertIs<IdentityOperationResult.Pending>(restarted.identities.resumePending(pending.identityId))
            provider.receipt = RecoveryReceipt.ConfirmedByProvider
            val active = assertIs<IdentityOperationResult.Active>(restarted.identities.resumePending(pending.identityId))
            assertEquals(pending.identityId, active.identity.id)
            assertEquals(RecoveryReceipt.ConfirmedByProvider, assertIs<IdentityRecoveryState.Submitted>(active.identity.recovery).receipt)
            provider.receipt = RecoveryReceipt.AcceptedLocally
            val backup = restarted.identities.backupOptions(active.identity.id).single()
            assertIs<IdentityOperationResult.Failed>(restarted.identities.backup(backup))
        }
    }

    private class Fixture(
        configuration: IdentityConfiguration? = null,
        val provider: MemoryRecovery = MemoryRecovery(),
    ) : AutoCloseable {
        private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        private val db = WalletPersistenceDatabase(driver)
        val queries = db.walletPersistenceQueries
        private val config = MobileWalletConfig(identity = configuration ?: IdentityConfiguration(
            recoveryProviders = listOf(provider), authorization = IdentityAuthorization.Explicit(KeyUseAuthorizationPolicy.None)))
        init { WalletPersistenceDatabase.Schema.create(driver) }
        val wallet = reopen()
        fun reopen(identity: IdentityConfiguration = config.identity) = createSqlDelightMobileWallet(config.copy(identity = identity), ClientIdTrustConfiguration(), db, NoNative, Crypto2DidService, {})
        override fun close() { driver.close() }
    }

    private class MemoryRecovery : IdentityRecoveryProvider {
        override val id = "test-memory"
        override val displayName = "Test-only memory provider"
        val records = mutableMapOf<String, ByteArray>()
        var corruptReadback = false
        var receipt = RecoveryReceipt.AcceptedLocally
        var failStore = false
        var available = true
        override suspend fun availability() = if (available) RecoveryAvailability.Available(RecoveryProtection.ApplicationEncrypted, RecoveryScope.Custom)
            else RecoveryAvailability.Unavailable("Fixture unavailable")
        override suspend fun list() = records.keys.toList()
        override suspend fun store(recordId: String, record: IdentityRecoveryData): RecoveryReceipt {
            check(!failStore)
            val bytes = record.copyBytes()
            check(records[recordId]?.contentEquals(bytes) != false)
            records[recordId] = bytes
            return receipt
        }
        override suspend fun retrieve(recordId: String) = records[recordId]?.let {
            IdentityRecoveryData(if (corruptReadback) "corrupt".encodeToByteArray() else it)
        }
        override suspend fun delete(recordId: String): RecoveryReceipt { records.remove(recordId); return RecoveryReceipt.ConfirmedByProvider }
    }

    private object NoNative : PlatformManagedKeyProvider {
        override suspend fun preflight(requirements: WalletKeyRequirements) =
            KeyUseAuthorizationSupport.Unsupported(KeyUseAuthorizationUnsupportedReason.UnsupportedCombination)
        override suspend fun generateManagedKey(request: WalletKeyCreationRequest): ManagedKey = error("Unavailable")
        override fun keyUseAuthorizationPolicy(stored: StoredKey.Managed): KeyUseAuthorizationPolicy = error("Unavailable")
        override suspend fun restoreManagedKey(stored: StoredKey.Managed): PlatformManagedKeyRestoration = error("Unavailable")
        override suspend fun deleteManagedKey(stored: StoredKey.Managed): Unit = error("Unavailable")
    }
}
