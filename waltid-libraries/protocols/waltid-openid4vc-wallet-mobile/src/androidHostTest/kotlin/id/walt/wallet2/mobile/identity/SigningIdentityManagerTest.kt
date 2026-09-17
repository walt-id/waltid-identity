@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.wallet2.mobile.identity

import id.walt.crypto2.keys.KeyUseAuthorizationPolicy
import id.walt.crypto2.keys.KeyUseAuthorizationSupport
import id.walt.crypto2.keys.KeyUseAuthorizationUnsupportedReason

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.*
import id.walt.crypto2.serialization.BinaryData
import id.walt.crypto2.serialization.StoredKeyCodec
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.ManagedKey
import id.walt.crypto2.keys.StoredKey
import id.walt.did.dids.Crypto2DidService
import id.walt.openid4vp.clientidprefix.ClientIdTrustConfiguration
import id.walt.wallet2.mobile.*
import id.walt.wallet2.mobile.createSqlDelightMobileWallet
import id.walt.wallet2.persistence.db.WalletPersistenceDatabase
import id.walt.wallet2.persistence.keys.*
import id.walt.wallet2.persistence.stores.SqlDelightKeyStore
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CancellationException
import kotlin.test.*

class SigningIdentityManagerTest {
    @Test fun `unavailable provider reason survives alongside a usable recovery route`() = runTest {
        val cloud = MemoryRecovery("cloud").apply { available = false }
        val transfer = MemoryRecovery("transfer")
        Fixture(SigningIdentityConfiguration(recoveryProviders = listOf(cloud, transfer),
            authorization = SigningIdentityAuthorization.Explicit(KeyUseAuthorizationPolicy.None))).use { fixture ->
            val statuses = fixture.wallet.signingIdentity.recoveryProviderStatuses()
            assertEquals(listOf("cloud", "transfer"), statuses.map { it.id })
            assertEquals("Fixture unavailable", assertIs<RecoveryAvailability.Unavailable>(statuses[0].availability).reason)
            assertIs<RecoveryAvailability.Available>(statuses[1].availability)
            val options = assertIs<SigningIdentityCreationOptions.Available>(fixture.wallet.signingIdentity.creationOptions(SigningIdentityIntent.Recoverable))
            assertTrue((listOf(options.recommended) + options.alternatives).all { it.providerId == "transfer" })
            cloud.available = true
            assertTrue(fixture.wallet.signingIdentity.recoveryProviderStatuses().all { it.availability is RecoveryAvailability.Available })
        }
    }

    @Test fun `provider service failure is isolated and redacted but cancellation propagates`() = runTest {
        val broken = MemoryRecovery("broken").apply { availabilityFailure = IllegalStateException("private service details") }
        val available = MemoryRecovery("available")
        Fixture(SigningIdentityConfiguration(recoveryProviders = listOf(broken, available),
            authorization = SigningIdentityAuthorization.Explicit(KeyUseAuthorizationPolicy.None))).use { fixture ->
            val status = fixture.wallet.signingIdentity.recoveryProviderStatuses().first()
            assertEquals("The recovery service could not be reached. Try again.",
                assertIs<RecoveryAvailability.Unavailable>(status.availability).reason)
            assertIs<SigningIdentityCreationOptions.Available>(fixture.wallet.signingIdentity.creationOptions(SigningIdentityIntent.Recoverable))
            broken.availabilityFailure = CancellationException("cancelled")
            assertFailsWith<CancellationException> { fixture.wallet.signingIdentity.recoveryProviderStatuses() }
        }
    }

    @Test fun `activation and reopening publish the registry outside the identity lock`() = runTest {
        lateinit var current: MobileWallet
        var notifications = 0
        Fixture(onRegistryChanged = {
            assertIs<SigningIdentityState.Active>(current.signingIdentity.state())
            notifications++
        }).use { fixture ->
            current = fixture.wallet
            val original = assertIs<SigningIdentityOperationResult.Active>(current.signingIdentity.initialize()).identity
            assertEquals(1, notifications)
            current = fixture.reopen()
            assertEquals(original, assertIs<SigningIdentityOperationResult.Active>(current.signingIdentity.initialize()).identity)
            assertEquals(2, notifications)
        }
    }

    @Test fun `pending setup is not published until resumed and recovery publishes its destination`() = runTest {
        var sourceNotifications = 0
        Fixture(onRegistryChanged = { sourceNotifications++ }).use { source ->
            source.provider.failStore = true
            val option = assertIs<SigningIdentityCreationOptions.Available>(source.wallet.signingIdentity.creationOptions(SigningIdentityIntent.Recoverable)).recommended
            val pending = assertIs<SigningIdentityOperationResult.Pending>(source.wallet.signingIdentity.create(option))
            assertEquals(0, sourceNotifications)
            source.provider.failStore = false
            val original = assertIs<SigningIdentityOperationResult.Active>(source.wallet.signingIdentity.resumePending(pending.identityId)).identity
            assertEquals(1, sourceNotifications)
            var destinationNotifications = 0
            Fixture(provider = source.provider, onRegistryChanged = { destinationNotifications++ }).use { destination ->
                val restore = destination.wallet.signingIdentity.restorationOptions(destination.wallet.signingIdentity.discoverRecovery().candidates.single()).single()
                val restored = assertIs<SigningIdentityOperationResult.Active>(destination.wallet.signingIdentity.restore(restore)).identity
                assertEquals(original.publicJwk, restored.publicJwk)
                assertEquals(1, destinationNotifications)
            }
        }
    }

    @Test fun `registration and host notification failures do not undo an active identity`() = runTest {
        val registry = object : MobileWalletCredentialRegistry {
            override val capabilities = UnavailableMobileWalletCredentialRegistry.capabilities
            override suspend fun replace(registryId: String, records: List<MobileWalletCredentialRegistryRecord>): MobileWalletCredentialRegistrationResult =
                error("Registry unavailable")
        }
        Fixture(registry = registry, onRegistryChanged = { error("Host notification failed") }).use { fixture ->
            val identity = assertIs<SigningIdentityOperationResult.Active>(fixture.wallet.signingIdentity.initialize()).identity
            assertEquals(identity, assertIs<SigningIdentityState.Active>(fixture.wallet.signingIdentity.state()).identity)
            assertEquals(false, fixture.wallet.digitalCredentialRegistration.value?.available)
            assertEquals("Registry unavailable", fixture.wallet.digitalCredentialRegistration.value?.reason)
        }
    }

    @Test fun `cancelled submission preserves accepted backup and resumes the same key after restart`() = runTest {
        Fixture().use { fixture ->
            fixture.provider.cancelAfterStore = true
            val option = assertIs<SigningIdentityCreationOptions.Available>(fixture.wallet.signingIdentity.creationOptions(SigningIdentityIntent.Recoverable)).recommended
            assertFailsWith<CancellationException> { fixture.wallet.signingIdentity.create(option) }
            val pending = assertIs<SigningIdentityState.Pending>(fixture.reopen().signingIdentity.state())
            val accepted = fixture.provider.records.getValue(pending.identityId).copyOf()
            val keyId = fixture.queries.selectAll().executeAsList().single().key_id
            fixture.provider.cancelAfterStore = false
            val active = assertIs<SigningIdentityOperationResult.Active>(fixture.reopen().signingIdentity.resumePending(pending.identityId)).identity
            assertEquals(keyId, active.keyId)
            assertTrue(accepted.contentEquals(fixture.provider.records.getValue(active.id)), "Retry changed the recovery record")
            assertEquals(1, fixture.queries.selectAll().executeAsList().size)
        }
    }

    @Test fun `missing unavailable and cancelled retrieval cannot create a destination key`() = runTest {
        Fixture().use { source ->
            val original = assertIs<SigningIdentityOperationResult.Active>(source.wallet.signingIdentity.create(
                assertIs<SigningIdentityCreationOptions.Available>(source.wallet.signingIdentity.creationOptions(SigningIdentityIntent.Recoverable)).recommended)).identity
            Fixture(provider = source.provider).use { destination ->
                val candidate = destination.wallet.signingIdentity.discoverRecovery().candidates.single()
                val option = destination.wallet.signingIdentity.restorationOptions(candidate).single()
                val record = source.provider.records.remove(original.id)!!
                assertEquals(SigningIdentityFailure.ProviderUnavailable,
                    assertIs<SigningIdentityOperationResult.Failed>(destination.wallet.signingIdentity.restore(option)).reason)
                source.provider.records[original.id] = record
                source.provider.available = false
                assertEquals(SigningIdentityFailure.ProviderUnavailable,
                    assertIs<SigningIdentityOperationResult.Failed>(destination.wallet.signingIdentity.restore(option)).reason)
                source.provider.available = true
                source.provider.retrieveFailure = IdentityProviderFailure.InteractionRequired
                assertEquals(SigningIdentityFailure.ProviderInteractionRequired,
                    assertIs<SigningIdentityOperationResult.Failed>(destination.wallet.signingIdentity.restore(option)).reason)
                source.provider.retrieveFailure = null
                source.provider.cancelRetrieve = true
                assertFailsWith<CancellationException> { destination.wallet.signingIdentity.restore(option) }
                assertEquals(SigningIdentityState.Absent, destination.reopen().signingIdentity.state())
                assertTrue(destination.queries.selectAll().executeAsList().isEmpty())
                source.provider.cancelRetrieve = false
                val restarted = destination.reopen()
                val retry = restarted.signingIdentity.restorationOptions(restarted.signingIdentity.discoverRecovery().candidates.single()).single()
                assertEquals(original.publicJwk, assertIs<SigningIdentityOperationResult.Active>(restarted.signingIdentity.restore(retry)).identity.publicJwk)
            }
        }
    }

    @Test fun `restart cleans interrupted restoration before allowing an explicit retry`() = runTest {
        Fixture().use { source ->
            val original = assertIs<SigningIdentityOperationResult.Active>(source.wallet.signingIdentity.create(
                assertIs<SigningIdentityCreationOptions.Available>(source.wallet.signingIdentity.creationOptions(SigningIdentityIntent.Recoverable)).recommended)).identity
            Fixture(provider = source.provider).use { destination ->
                val option = destination.wallet.signingIdentity.restorationOptions(destination.wallet.signingIdentity.discoverRecovery().candidates.single()).single()
                assertIs<SigningIdentityOperationResult.Active>(destination.wallet.signingIdentity.restore(option))
                // Emulate process death after key import/journaling but before active binding was committed.
                val stored = recordJson.decodeFromString<IdentityRecord>(destination.queries.selectIdentityRecord("default").executeAsOne().payload)
                destination.queries.putIdentityRecord("default", IdentityPhase.Preparing.name,
                    recordJson.encodeToString(stored.copy(phase = IdentityPhase.Preparing)))
                val restarted = destination.reopen()
                assertIs<SigningIdentityState.Pending>(restarted.signingIdentity.state())
                assertIs<SigningIdentityOperationResult.Failed>(restarted.signingIdentity.resumePending(original.id))
                assertEquals(SigningIdentityState.Absent, restarted.signingIdentity.state())
                assertTrue(destination.queries.selectAll().executeAsList().isEmpty())
                assertNotNull(source.provider.records[original.id])
                val retry = restarted.signingIdentity.restorationOptions(restarted.signingIdentity.discoverRecovery().candidates.single()).single()
                assertEquals(original.publicJwk, assertIs<SigningIdentityOperationResult.Active>(restarted.signingIdentity.restore(retry)).identity.publicJwk)
            }
        }
    }
    @Test fun `default configuration does not expose recoverable or unauthenticated software options`() = runTest {
        Fixture(SigningIdentityConfiguration()).use { fixture ->
            assertIs<SigningIdentityCreationOptions.Unavailable>(fixture.wallet.signingIdentity.creationOptions(SigningIdentityIntent.Recoverable))
            assertIs<SigningIdentityCreationOptions.Unavailable>(fixture.wallet.signingIdentity.creationOptions())
            assertTrue(fixture.provider.records.isEmpty())
        }
    }

    @Test fun `software creation persists explicit active binding and restarts`() = runTest {
        Fixture().use { fixture ->
            val options = assertIs<SigningIdentityCreationOptions.Available>(fixture.wallet.signingIdentity.creationOptions())
            assertEquals(SigningIdentityKeyStorage.EncryptedDatabase, options.recommended.storage)
            val created = assertIs<SigningIdentityOperationResult.Active>(fixture.wallet.signingIdentity.create(options.recommended)).identity
            assertTrue(created.did.startsWith("did:jwk:"))
            assertFalse(created.publicJwk.contains("\"d\""))
            assertEquals(created.keyId, fixture.queries.selectActiveIdentity("default").executeAsOne().key_id)
            assertEquals(created, assertIs<SigningIdentityState.Active>(fixture.reopen().signingIdentity.state()).identity)
            assertIs<SigningIdentityOperationResult.Failed>(fixture.wallet.signingIdentity.create(options.recommended))
        }
    }

    @Test fun `backup failure remains pending across restart and retry activates the original identity`() = runTest {
        Fixture().use { fixture ->
            fixture.provider.failStore = true
            val option = assertIs<SigningIdentityCreationOptions.Available>(fixture.wallet.signingIdentity.creationOptions(SigningIdentityIntent.Recoverable)).recommended
            val pending = assertIs<SigningIdentityOperationResult.Pending>(fixture.wallet.signingIdentity.create(option))
            assertNull(fixture.queries.selectActiveIdentity("default").executeAsOneOrNull())
            assertNull(SqlDelightKeyStore(NoNative, fixture.queries, "default").getDefaultCrypto2Key())
            val restarted = fixture.reopen()
            assertEquals(pending.identityId, assertIs<SigningIdentityState.Pending>(restarted.signingIdentity.state()).identityId)
            fixture.provider.failStore = false
            val active = assertIs<SigningIdentityOperationResult.Active>(restarted.signingIdentity.resumePending(pending.identityId))
            assertEquals(pending.identityId, active.identity.id)
            assertEquals(1, fixture.queries.selectAll().executeAsList().size)
            assertEquals(RecoveryReceipt.AcceptedLocally, assertIs<SigningIdentityRecoveryState.Submitted>(active.identity.recovery).receipt)
        }
    }

    @Test fun `recovery preserves exact DID public key and key ID in a fresh database`() = runTest {
        Fixture().use { original ->
            val option = assertIs<SigningIdentityCreationOptions.Available>(original.wallet.signingIdentity.creationOptions(SigningIdentityIntent.Recoverable)).recommended
            val expected = assertIs<SigningIdentityOperationResult.Active>(original.wallet.signingIdentity.create(option)).identity
            Fixture(provider = original.provider).use { destination ->
                val candidate = destination.wallet.signingIdentity.discoverRecovery().candidates.single()
                val restore = destination.wallet.signingIdentity.restorationOptions(candidate).single()
                val restored = assertIs<SigningIdentityOperationResult.Active>(destination.wallet.signingIdentity.restore(restore)).identity
                assertEquals(expected.did, restored.did)
                assertEquals(expected.publicJwk, restored.publicJwk)
                assertEquals(expected.keyId, restored.keyId)
                assertEquals(expected.id, restored.id)
            }
        }
    }

    @Test fun `options cannot cross wallet instances and provider changes invalidate creation`() = runTest {
        Fixture().use { fixture ->
            val option = assertIs<SigningIdentityCreationOptions.Available>(fixture.wallet.signingIdentity.creationOptions(SigningIdentityIntent.Recoverable)).recommended
            assertEquals(SigningIdentityFailure.StaleOption, assertIs<SigningIdentityOperationResult.Failed>(fixture.reopen().signingIdentity.create(option)).reason)
            fixture.provider.available = false
            assertEquals(SigningIdentityFailure.StaleOption, assertIs<SigningIdentityOperationResult.Failed>(fixture.wallet.signingIdentity.create(option)).reason)
            assertTrue(fixture.queries.selectAll().executeAsList().isEmpty())
        }
    }

    @Test fun `device-bound host policy prevents enabling backup for exportable software keys`() = runTest {
        Fixture(configuration = SigningIdentityConfiguration(authorization = SigningIdentityAuthorization.Explicit(KeyUseAuthorizationPolicy.None),
            policy = SigningIdentityKeyPolicy.BackupAndCustodyDisabled)).use { fixture ->
            val option = assertIs<SigningIdentityCreationOptions.Available>(fixture.wallet.signingIdentity.creationOptions()).recommended
            val created = assertIs<SigningIdentityOperationResult.Active>(fixture.wallet.signingIdentity.create(option)).identity
            assertTrue(fixture.wallet.signingIdentity.backupOptions(created.id).isEmpty())
            assertIs<SigningIdentityCreationOptions.Unavailable>(fixture.wallet.signingIdentity.creationOptions(SigningIdentityIntent.Recoverable))
        }
    }

    @Test fun `enabling backup for an existing software identity exports its original private key`() = runTest {
        Fixture().use { fixture ->
            val created = assertIs<SigningIdentityOperationResult.Active>(fixture.wallet.signingIdentity.create(
                assertIs<SigningIdentityCreationOptions.Available>(fixture.wallet.signingIdentity.creationOptions()).recommended)).identity
            val result = assertIs<SigningIdentityOperationResult.Active>(fixture.wallet.signingIdentity.backup(
                fixture.wallet.signingIdentity.backupOptions(created.id).single()))
            assertEquals(created.did, result.identity.did)
            val record = recordJson.decodeFromString<RecoveryRecord>(fixture.provider.records.values.single().decodeToString())
            assertIs<RecoverySecret.Exported>(record.secret)
            Fixture(provider = fixture.provider).use { destination ->
                val option = destination.wallet.signingIdentity.restorationOptions(destination.wallet.signingIdentity.discoverRecovery().candidates.single()).single()
                assertEquals(created.publicJwk, assertIs<SigningIdentityOperationResult.Active>(destination.wallet.signingIdentity.restore(option)).identity.publicJwk)
            }
        }
    }

    @Test fun `corrupted or changed recovery record cannot create a destination key`() = runTest {
        Fixture().use { original ->
            val created = assertIs<SigningIdentityOperationResult.Active>(original.wallet.signingIdentity.create(
                assertIs<SigningIdentityCreationOptions.Available>(original.wallet.signingIdentity.creationOptions(SigningIdentityIntent.Recoverable)).recommended)).identity
            Fixture(provider = original.provider).use { destination ->
                val candidate = destination.wallet.signingIdentity.discoverRecovery().candidates.single()
                val option = destination.wallet.signingIdentity.restorationOptions(candidate).single()
                original.provider.records[created.id] = "{}".encodeToByteArray()
                assertTrue(destination.wallet.signingIdentity.restorationOptions(candidate).isEmpty())
                assertEquals(SigningIdentityFailure.StaleOption, assertIs<SigningIdentityOperationResult.Failed>(destination.wallet.signingIdentity.restore(option)).reason)
                assertTrue(destination.queries.selectAll().executeAsList().isEmpty())
            }
        }
    }

    @Test fun `local recovery retention and provider deletion have separate effects`() = runTest {
        val provider = MemoryRecovery()
        Fixture(SigningIdentityConfiguration(recoveryProviders = listOf(provider),
            authorization = SigningIdentityAuthorization.Explicit(KeyUseAuthorizationPolicy.None),
            localRecoveryMaterial = LocalRecoveryMaterialRetention.DiscardAfterConfirmation), provider).use { fixture ->
            val option = assertIs<SigningIdentityCreationOptions.Available>(fixture.wallet.signingIdentity.creationOptions(SigningIdentityIntent.Recoverable)).recommended
            val created = assertIs<SigningIdentityOperationResult.Active>(fixture.wallet.signingIdentity.create(option)).identity
            val record = recordJson.decodeFromString<IdentityRecord>(fixture.queries.selectIdentityRecord("default").executeAsOne().payload)
            assertNull(record.recovery)
            assertEquals(1, provider.records.size)
            val originalRecovery = provider.records.getValue(created.id).copyOf()
            assertIs<SigningIdentityOperationResult.Active>(fixture.wallet.signingIdentity.backup(
                fixture.wallet.signingIdentity.backupOptions(created.id).single()))
            assertContentEquals(originalRecovery, provider.records.getValue(created.id))
            assertEquals(RecoveryReceipt.ConfirmedByProvider, fixture.wallet.signingIdentity.deleteRecovery(
                fixture.wallet.signingIdentity.discoverRecovery().candidates.single()))
            assertTrue(provider.records.isEmpty())
            val active = assertIs<SigningIdentityState.Active>(fixture.wallet.signingIdentity.state()).identity
            assertEquals(created.keyId, active.keyId)
            assertIs<SigningIdentityRecoveryState.RemovalRequested>(active.recovery)
            assertNotNull(SqlDelightKeyStore(NoNative, fixture.queries, "default").getDefaultKeyMaterial())
        }
    }

    @Test fun `missing identity association cannot silently replace an existing key`() = runTest {
        Fixture().use { fixture ->
            val created = assertIs<SigningIdentityOperationResult.Active>(fixture.wallet.signingIdentity.initialize()).identity
            fixture.queries.deleteIdentityRecord("default")
            assertEquals(SigningIdentityFailure.KeyUnavailable, assertIs<SigningIdentityState.Unavailable>(fixture.wallet.signingIdentity.state()).reason)
            assertIs<SigningIdentityOperationResult.Failed>(fixture.wallet.signingIdentity.initialize())
            assertEquals(created.keyId, fixture.queries.selectAll().executeAsList().single().key_id)
        }
    }

    @Test fun `pending default cannot be resolved by issuance key material path`() = runTest {
        Fixture().use { fixture ->
            fixture.provider.failStore = true
            val option = assertIs<SigningIdentityCreationOptions.Available>(fixture.wallet.signingIdentity.creationOptions(SigningIdentityIntent.Recoverable)).recommended
            assertIs<SigningIdentityOperationResult.Pending>(fixture.wallet.signingIdentity.create(option))
            assertNull(SqlDelightKeyStore(NoNative, fixture.queries, "default").getDefaultKeyMaterial())
        }
    }

    @Test fun `default initialization never selects a weaker authorization alternative`() = runTest {
        Fixture(SigningIdentityConfiguration(alternativeAuthorizations = listOf(KeyUseAuthorizationPolicy.None))).use { fixture ->
            assertIs<SigningIdentityCreationOptions.Available>(fixture.wallet.signingIdentity.creationOptions())
            assertEquals(SigningIdentityFailure.UnsupportedPolicy, assertIs<SigningIdentityOperationResult.Failed>(fixture.wallet.signingIdentity.initialize()).reason)
            assertTrue(fixture.queries.selectAll().executeAsList().isEmpty())
        }
    }

    @Test fun `cancel pending setup cleans local material and permits a new explicit attempt`() = runTest {
        Fixture().use { fixture ->
            fixture.provider.failStore = true
            val option = assertIs<SigningIdentityCreationOptions.Available>(fixture.wallet.signingIdentity.creationOptions(SigningIdentityIntent.Recoverable)).recommended
            val pending = assertIs<SigningIdentityOperationResult.Pending>(fixture.wallet.signingIdentity.create(option))
            fixture.wallet.signingIdentity.cancelPending(pending.identityId)
            fixture.wallet.signingIdentity.cancelPending(pending.identityId)
            assertEquals(SigningIdentityState.Absent, fixture.wallet.signingIdentity.state())
            assertTrue(fixture.queries.selectAll().executeAsList().isEmpty())
        }
    }

    @Test fun `wrong record identifier private public metadata and wrong derivation are rejected before native import`() = runTest {
        Fixture().use { source ->
            val identity = assertIs<SigningIdentityOperationResult.Active>(source.wallet.signingIdentity.create(
                assertIs<SigningIdentityCreationOptions.Available>(source.wallet.signingIdentity.creationOptions(SigningIdentityIntent.Recoverable)).recommended)).identity
            val original = recordJson.decodeFromString<RecoveryRecord>(source.provider.records.getValue(identity.id).decodeToString())
            assertIs<RecoverySecret.Exported>(original.secret)
            val invalid = listOf(
                original.copy(identityId = "different-record"),
                original.copy(publicJwk = original.publicJwk.dropLast(1) + ",\"d\":\"secret\"}"),
                original.copy(secret = RecoverySecret.Derived(recoveryBase64.encode(ByteArray(32)), "different-domain")),
                original.copy(format = "unknown-format"),
                original.copy(version = 2),
            )
            Fixture(provider = source.provider).use { destination ->
                val candidate = destination.wallet.signingIdentity.discoverRecovery().candidates.single()
                for (record in invalid) {
                    source.provider.records[identity.id] = record.encode()
                    assertTrue(destination.wallet.signingIdentity.restorationOptions(candidate).isEmpty())
                    assertTrue(destination.queries.selectAll().executeAsList().isEmpty())
                }
            }
        }
    }

    @Test fun `provider acknowledgement without exact readback never activates an identity`() = runTest {
        Fixture().use { fixture ->
            fixture.provider.corruptReadback = true
            val result = fixture.wallet.signingIdentity.create(
                assertIs<SigningIdentityCreationOptions.Available>(fixture.wallet.signingIdentity.creationOptions(SigningIdentityIntent.Recoverable)).recommended)
            val pending = assertIs<SigningIdentityOperationResult.Pending>(result)
            assertNull(fixture.queries.selectActiveIdentity("default").executeAsOneOrNull())
            fixture.provider.corruptReadback = false
            assertIs<SigningIdentityOperationResult.Active>(fixture.wallet.signingIdentity.resumePending(pending.identityId))
        }
    }

    @Test fun `pending setup must not activate after its local key disappears`() = runTest {
        Fixture().use { f ->
            f.provider.failStore = true
            val option = assertIs<SigningIdentityCreationOptions.Available>(f.wallet.signingIdentity.creationOptions(SigningIdentityIntent.Recoverable)).recommended
            val pending = assertIs<SigningIdentityOperationResult.Pending>(f.wallet.signingIdentity.create(option))
            val keyId = f.queries.selectAll().executeAsList().single().key_id
            assertTrue(SqlDelightKeyStore(NoNative, f.queries, "default").removeKey(keyId))
            f.provider.failStore = false
            val restarted = f.reopen()
            val result = restarted.signingIdentity.resumePending(pending.identityId)
            assertFalse(result is SigningIdentityOperationResult.Active, "Activated missing key: $result; actual state=${restarted.signingIdentity.state()}")
        }
    }
    @Test fun `pending setup must respect newly configured device-bound policy before upload`() = runTest {
        Fixture().use { f ->
            f.provider.failStore = true
            val option = assertIs<SigningIdentityCreationOptions.Available>(f.wallet.signingIdentity.creationOptions(SigningIdentityIntent.Recoverable)).recommended
            val pending = assertIs<SigningIdentityOperationResult.Pending>(f.wallet.signingIdentity.create(option))
            f.provider.failStore = false
            val restarted = f.reopen(SigningIdentityConfiguration(recoveryProviders = listOf(f.provider),
                authorization = SigningIdentityAuthorization.Explicit(KeyUseAuthorizationPolicy.None), policy = SigningIdentityKeyPolicy.BackupAndCustodyDisabled))
            restarted.signingIdentity.resumePending(pending.identityId)
            assertTrue(f.provider.records.isEmpty(), "Uploaded a secret after the host prohibited backup")
        }
    }
    @Test fun `required provider confirmation survives restart and retains recovery until confirmed`() = runTest {
        val provider = MemoryRecovery()
        val configuration = SigningIdentityConfiguration(recoveryProviders = listOf(provider),
            authorization = SigningIdentityAuthorization.Explicit(KeyUseAuthorizationPolicy.None),
            recoveryConfirmation = RecoveryConfirmation.ProviderConfirmation,
            localRecoveryMaterial = LocalRecoveryMaterialRetention.DiscardAfterConfirmation)
        Fixture(configuration, provider).use { fixture ->
            val option = assertIs<SigningIdentityCreationOptions.Available>(fixture.wallet.signingIdentity.creationOptions(SigningIdentityIntent.Recoverable)).recommended
            val pending = assertIs<SigningIdentityOperationResult.Pending>(fixture.wallet.signingIdentity.create(option))
            assertNull(fixture.queries.selectActiveIdentity("default").executeAsOneOrNull())
            val restarted = fixture.reopen(configuration.copy(recoveryConfirmation = RecoveryConfirmation.LocalAcceptance))
            assertIs<SigningIdentityOperationResult.Pending>(restarted.signingIdentity.resumePending(pending.identityId))
            provider.receipt = RecoveryReceipt.ConfirmedByProvider
            val active = assertIs<SigningIdentityOperationResult.Active>(restarted.signingIdentity.resumePending(pending.identityId))
            assertEquals(pending.identityId, active.identity.id)
            assertEquals(RecoveryReceipt.ConfirmedByProvider, assertIs<SigningIdentityRecoveryState.Submitted>(active.identity.recovery).receipt)
            provider.receipt = RecoveryReceipt.AcceptedLocally
            val backup = restarted.signingIdentity.backupOptions(active.identity.id).single()
            assertIs<SigningIdentityOperationResult.Failed>(restarted.signingIdentity.backup(backup))
        }
    }

    @Test fun `restoration preserves recorded minimums instead of weakening them to destination defaults`() = runTest {
        Fixture().use { source ->
            val option = assertIs<SigningIdentityCreationOptions.Available>(source.wallet.signingIdentity.creationOptions(SigningIdentityIntent.Recoverable)).recommended
            val original = assertIs<SigningIdentityOperationResult.Active>(source.wallet.signingIdentity.create(option)).identity
            val bytes = source.provider.records.getValue(original.id)
            val record = recordJson.decodeFromString<RecoveryRecord>(bytes.decodeToString())
            assertEquals(SigningIdentityKeyStorage.EncryptedDatabase, record.constraints.storage)
            assertEquals(KeyUseAuthorizationPolicy.None, record.constraints.authorization)
            for (restricted in listOf(
                record.constraints.copy(storage = SigningIdentityKeyStorage.HardwareBacked),
                record.constraints.copy(authorization = KeyUseAuthorizationPolicy.BiometricCurrentSet),
            )) {
                source.provider.records[original.id] = record.copy(constraints = restricted).encode()
                Fixture(provider = source.provider).use { destination ->
                    val candidate = destination.wallet.signingIdentity.discoverRecovery().candidates.single()
                    assertTrue(destination.wallet.signingIdentity.restorationOptions(candidate).isEmpty())
                    assertTrue(destination.queries.selectAll().executeAsList().isEmpty())
                }
            }
        }
    }

    @Test fun `provider failures remain actionable after restarting pending setup`() = runTest {
        val cases = mapOf(
            IdentityProviderFailure.TemporarilyUnavailable to SigningIdentityFailure.ProviderUnavailable,
            IdentityProviderFailure.InteractionRequired to SigningIdentityFailure.ProviderInteractionRequired,
            IdentityProviderFailure.Rejected to SigningIdentityFailure.ProviderRejected,
            IdentityProviderFailure.Conflict to SigningIdentityFailure.ProviderConflict,
            IdentityProviderFailure.ConfirmationPending to SigningIdentityFailure.ProviderConfirmationPending,
        )
        for ((cause, expected) in cases) Fixture().use { fixture ->
            fixture.provider.failure = cause
            val option = assertIs<SigningIdentityCreationOptions.Available>(fixture.wallet.signingIdentity.creationOptions(SigningIdentityIntent.Recoverable)).recommended
            val pending = assertIs<SigningIdentityOperationResult.Pending>(fixture.wallet.signingIdentity.create(option))
            assertEquals(expected, pending.reason)
            assertEquals(expected, assertIs<SigningIdentityState.Pending>(fixture.reopen().signingIdentity.state()).reason)
            assertNull(fixture.queries.selectActiveIdentity("default").executeAsOneOrNull())
            fixture.provider.failure = null
            assertIs<SigningIdentityOperationResult.Active>(fixture.wallet.signingIdentity.resumePending(pending.identityId))
        }
    }

    @Test fun `custody preserves local identity and recovery state and persists one verified reference`() = runTest {
        val custodian = MemoryCustodian()
        Fixture(SigningIdentityConfiguration(keyCustodians = listOf(custodian),
            authorization = SigningIdentityAuthorization.Explicit(KeyUseAuthorizationPolicy.None))).use { fixture ->
            val original = assertIs<SigningIdentityOperationResult.Active>(fixture.wallet.signingIdentity.initialize()).identity
            val option = fixture.wallet.signingIdentity.custodyOptions(original.id).single()
            repeat(2) { assertIs<SigningIdentityCustodyResult.Imported>(fixture.wallet.signingIdentity.copyToCustody(option)) }
            val active = assertIs<SigningIdentityState.Active>(fixture.reopen().signingIdentity.state()).identity
            assertEquals(original.did, active.did)
            assertEquals(original.publicJwk, active.publicJwk)
            assertEquals(original.recovery, active.recovery)
            assertEquals(listOf(IdentityCustodyReference(custodian.id, "kms/${original.keyId}")), active.custody)
            assertNotNull(SqlDelightKeyStore(NoNative, fixture.queries, "default").getDefaultKeyMaterial())
            assertEquals(2, custodian.imports)
            assertTrue(custodian.privateJwk!!.contains("\"d\""))
            assertEquals(SigningIdentityFailure.StaleOption,
                assertIs<SigningIdentityCustodyResult.Failed>(fixture.reopen().signingIdentity.copyToCustody(option)).reason)
        }
    }

    @Test fun `custody conflicts never record verified custody and restricted identities never offer export`() = runTest {
        val custodian = MemoryCustodian()
        val config = SigningIdentityConfiguration(keyCustodians = listOf(custodian),
            authorization = SigningIdentityAuthorization.Explicit(KeyUseAuthorizationPolicy.None))
        Fixture(config).use { fixture ->
            val original = assertIs<SigningIdentityOperationResult.Active>(fixture.wallet.signingIdentity.initialize()).identity
            Fixture().use { other ->
                custodian.returnedJwk = assertIs<SigningIdentityOperationResult.Active>(other.wallet.signingIdentity.initialize()).identity.publicJwk
            }
            val option = fixture.wallet.signingIdentity.custodyOptions(original.id).single()
            assertEquals(SigningIdentityFailure.ProviderConflict,
                assertIs<SigningIdentityCustodyResult.Failed>(fixture.wallet.signingIdentity.copyToCustody(option)).reason)
            assertTrue(assertIs<SigningIdentityState.Active>(fixture.wallet.signingIdentity.state()).identity.custody.isEmpty())
            val restricted = fixture.reopen(config.copy(policy = SigningIdentityKeyPolicy.BackupAndCustodyDisabled))
            assertTrue(restricted.signingIdentity.custodyOptions(original.id).isEmpty())
        }
        Fixture(config.copy(policy = SigningIdentityKeyPolicy.BackupAndCustodyDisabled)).use { fixture ->
            val original = assertIs<SigningIdentityOperationResult.Active>(fixture.wallet.signingIdentity.initialize()).identity
            assertTrue(fixture.wallet.signingIdentity.custodyOptions(original.id).isEmpty())
            assertTrue(fixture.reopen(config).signingIdentity.custodyOptions(original.id).isEmpty())
        }
    }

    @Test fun `issuance restrictions require retained identity policy and reject a different key`() = runTest {
        for (policy in listOf(SigningIdentityKeyPolicy.GeneralPurpose, SigningIdentityKeyPolicy.BackupAndCustodyDisabled)) {
            Fixture(SigningIdentityConfiguration(policy = policy,
                authorization = SigningIdentityAuthorization.Explicit(KeyUseAuthorizationPolicy.None))).use { fixture ->
                val identity = assertIs<SigningIdentityOperationResult.Active>(fixture.wallet.signingIdentity.initialize()).identity
                val reopened = fixture.reopen().signingIdentity
                reopened.requireKeyPolicy(identity.keyId, SigningIdentityKeyPolicy.GeneralPurpose)
                if (policy == SigningIdentityKeyPolicy.BackupAndCustodyDisabled) reopened.requireKeyPolicy(identity.keyId, policy)
                else assertFailsWith<IllegalArgumentException> { reopened.requireKeyPolicy(identity.keyId, SigningIdentityKeyPolicy.BackupAndCustodyDisabled) }
                assertFailsWith<IllegalArgumentException> { reopened.requireKeyPolicy(identity.keyId, SigningIdentityKeyPolicy.HardwareGenerated) }
                assertFailsWith<IllegalArgumentException> { reopened.requireKeyPolicy("different", SigningIdentityKeyPolicy.BackupAndCustodyDisabled) }
            }
        }
    }


    @Test fun `discovery survives one provider list outage`() = runTest {
        val broken = MemoryRecovery("broken").apply { listFailure = IdentityProviderFailure.TemporarilyUnavailable }
        val healthy = MemoryRecovery("healthy")
        healthy.records["recoverable-identity"] = byteArrayOf(1)
        Fixture(SigningIdentityConfiguration(recoveryProviders = listOf(broken, healthy),
            authorization = SigningIdentityAuthorization.Explicit(KeyUseAuthorizationPolicy.None))).use { fixture ->
            val discovery = fixture.wallet.signingIdentity.discoverRecovery()
            assertEquals(listOf("healthy"), discovery.candidates.map { it.reference.providerId })
            assertEquals(listOf(SigningIdentityRecoveryProviderFailure("broken", broken.displayName, SigningIdentityFailure.ProviderUnavailable)), discovery.failures)
            broken.availabilityFailure = CancellationException("cancelled")
            assertFailsWith<CancellationException> { fixture.wallet.signingIdentity.discoverRecovery() }
        }
    }

    @Test fun `execution availability errors are safe and do not activate a key`() = runTest {
        Fixture().use { fixture ->
            val manager = fixture.wallet.signingIdentity
            val option = assertIs<SigningIdentityCreationOptions.Available>(manager.creationOptions(SigningIdentityIntent.Recoverable)).recommended
            fixture.provider.availabilityFailure = IllegalStateException("private details")
            assertEquals(SigningIdentityFailure.ProviderUnavailable, assertIs<SigningIdentityOperationResult.Failed>(manager.create(option)).reason)
            assertEquals(SigningIdentityState.Absent, manager.state())
            fixture.provider.availabilityFailure = null
            val identity = assertIs<SigningIdentityOperationResult.Active>(manager.create(option)).identity
            val backup = manager.backupOptions(identity.id).single()
            fixture.provider.availabilityFailure = IllegalStateException("private details")
            assertEquals(SigningIdentityFailure.ProviderUnavailable, assertIs<SigningIdentityOperationResult.Failed>(manager.backup(backup)).reason)
            assertEquals(identity, assertIs<SigningIdentityState.Active>(manager.state()).identity)
        }
    }

    @Test fun `candidate retrieval failures are typed and cancellation propagates`() = runTest {
        Fixture().use { fixture ->
            val manager = fixture.wallet.signingIdentity
            manager.create(assertIs<SigningIdentityCreationOptions.Available>(manager.creationOptions(SigningIdentityIntent.Recoverable)).recommended)
            val candidate = manager.discoverRecovery().candidates.single()
            fixture.provider.retrieveFailure = IdentityProviderFailure.InteractionRequired
            assertEquals(IdentityProviderFailure.InteractionRequired,
                assertFailsWith<IdentityProviderException> { manager.restorationOptions(candidate) }.failure)
            fixture.provider.retrieveFailure = null
            fixture.provider.cancelRetrieve = true
            assertFailsWith<CancellationException> { manager.restorationOptions(candidate) }
        }
    }

    @Test fun `initialize preserves pending failure after restart`() = runTest {
        Fixture().use { fixture ->
            fixture.provider.failure = IdentityProviderFailure.InteractionRequired
            val option = assertIs<SigningIdentityCreationOptions.Available>(fixture.wallet.signingIdentity.creationOptions(SigningIdentityIntent.Recoverable)).recommended
            assertEquals(SigningIdentityFailure.ProviderInteractionRequired,
                assertIs<SigningIdentityOperationResult.Pending>(fixture.wallet.signingIdentity.create(option)).reason)
            assertEquals(SigningIdentityFailure.ProviderInteractionRequired,
                assertIs<SigningIdentityOperationResult.Pending>(fixture.reopen().signingIdentity.initialize()).reason)
        }
    }

    @Test fun `legacy derived backups restore and pending retries retain original bytes`() = runTest {
        Fixture().use { source ->
            val id = "legacy-fixture"
            val keyId = "legacy-key"
            val seed = ByteArray(32) { it.toByte() }
            val material = LegacyRecoveryDerivation.derive(seed, id)
            val publicJwk = material.toPublicJwk(identitySpec).data.toByteArray().decodeToString()
            val did = "did:jwk:" + recoveryBase64.encode(publicJwk.encodeToByteArray())
            val reference = IdentityBackupReference(source.provider.id, id)
            val recovery = RecoveryRecord(identityId = id, keyId = keyId, did = did, publicJwk = publicJwk,
                secret = RecoverySecret.Derived(recoveryBase64.encode(seed), id),
                constraints = RecoveryConstraints(SigningIdentityKeyStorage.EncryptedDatabase, KeyUseAuthorizationPolicy.None, RecoveryConfirmation.LocalAcceptance))
            val identity = SigningIdentity(id, keyId, did, publicJwk, SigningIdentityKeyStorage.EncryptedDatabase,
                KeyUseAuthorizationPolicy.None, PlatformKeyFacts(KeyOrigin.IMPORTED, KeySecurityLevel.SOFTWARE, KeyProtectionLevel.SOFTWARE))
            val pending = IdentityRecord(id = id, keyId = keyId, phase = IdentityPhase.AwaitingBackup,
                storage = identity.storage, requirements = WalletKeyRequirements(identitySpec, identityUsages),
                policy = SigningIdentityKeyPolicy.GeneralPurpose, identity = identity, recovery = recovery,
                backup = reference, recoveryAvailability = source.provider.availability() as RecoveryAvailability.Available)
            val originalBytes = recovery.encode()
            source.provider.records[id] = originalBytes.copyOf()
            source.queries.insert(keyId, 0, StoredKeyCodec.encodeToString(
                StoredKey.Software(StoredKey.CURRENT_VERSION, KeyId(keyId), identitySpec, identityUsages, material)))
            source.queries.insertDid(did, "{}")
            source.queries.putIdentityRecord("default", pending.phase.name, recordJson.encodeToString(pending))
            assertEquals(did, assertIs<SigningIdentityOperationResult.Active>(source.reopen().signingIdentity.resumePending(id)).identity.did)
            assertContentEquals(originalBytes, source.provider.records.getValue(id))
            Fixture(provider = source.provider).use { destination ->
                val manager = destination.wallet.signingIdentity
                val option = manager.restorationOptions(manager.discoverRecovery().candidates.single()).single()
                val restored = assertIs<SigningIdentityOperationResult.Active>(manager.restore(option)).identity
                assertEquals(did, restored.did)
                assertEquals(publicJwk, restored.publicJwk)
                assertEquals(keyId, restored.keyId)
            }
        }
    }

    @Test fun `only missing or permanently invalidated native material permits repair`() = runTest {
        for (outcome in NativeFixture.Outcome.entries) {
            val native = NativeFixture()
            Fixture(native = native).use { fixture ->
                val manager = fixture.wallet.signingIdentity
                val choices = assertIs<SigningIdentityCreationOptions.Available>(manager.creationOptions(SigningIdentityIntent.Recoverable))
                val selected = (listOf(choices.recommended) + choices.alternatives).single { it.storage == SigningIdentityKeyStorage.NativeStorage }
                val original = assertIs<SigningIdentityOperationResult.Active>(manager.create(selected)).identity
                assertIs<RecoverySecret.Exported>(recordJson.decodeFromString<RecoveryRecord>(fixture.provider.records.getValue(original.id).decodeToString()).secret)
                assertEquals(1, fixture.queries.selectAll().executeAsList().size, "Native creation must not retain an operational software key")
                val restore = manager.restorationOptions(manager.discoverRecovery().candidates.single()).single { it.storage == selected.storage }
                native.outcome = outcome
                val result = manager.restore(restore)
                if (outcome == NativeFixture.Outcome.Missing || outcome == NativeFixture.Outcome.Invalidated) {
                    assertEquals(original.publicJwk, assertIs<SigningIdentityOperationResult.Active>(result).identity.publicJwk)
                    assertEquals(2, native.imports)
                } else {
                    assertIs<SigningIdentityOperationResult.Failed>(result)
                    assertEquals(1, native.imports)
                }
            }
        }
    }

    @Test fun `failed native repair rolls back the original journal and descriptor atomically`() = runTest {
        val native = NativeFixture()
        Fixture(native = native).use { fixture ->
            val manager = fixture.wallet.signingIdentity
            val choices = assertIs<SigningIdentityCreationOptions.Available>(manager.creationOptions(SigningIdentityIntent.Recoverable))
            val selected = (listOf(choices.recommended) + choices.alternatives).single { it.storage == SigningIdentityKeyStorage.NativeStorage }
            val identity = assertIs<SigningIdentityOperationResult.Active>(manager.create(selected)).identity
            val originalRecord = fixture.queries.selectIdentityRecord("default").executeAsOne().payload
            val originalKey = fixture.queries.selectByKeyId(identity.keyId).executeAsOne().stored_key
            val restore = manager.restorationOptions(manager.discoverRecovery().candidates.single()).single { it.storage == selected.storage }
            native.outcome = NativeFixture.Outcome.Invalidated
            native.failImport = true
            assertIs<SigningIdentityOperationResult.Failed>(manager.restore(restore))
            assertEquals(originalRecord, fixture.queries.selectIdentityRecord("default").executeAsOne().payload)
            assertEquals(originalKey, fixture.queries.selectByKeyId(identity.keyId).executeAsOne().stored_key)
            assertEquals(identity.id, fixture.queries.selectActiveIdentity("default").executeAsOne().identity_id)
            native.failImport = false
            assertEquals(identity.publicJwk, assertIs<SigningIdentityOperationResult.Active>(manager.restore(restore)).identity.publicJwk)
        }
    }

    @Test fun `malformed journal phases and nested rollback predecessors cannot be adopted`() = runTest {
        Fixture(native = NativeFixture()).use { fixture ->
            val manager = fixture.wallet.signingIdentity
            val choices = assertIs<SigningIdentityCreationOptions.Available>(manager.creationOptions(SigningIdentityIntent.Recoverable))
            manager.create((listOf(choices.recommended) + choices.alternatives).single { it.storage == SigningIdentityKeyStorage.NativeStorage })
            val original = fixture.queries.selectIdentityRecord("default").executeAsOne()
            val record = recordJson.decodeFromString<IdentityRecord>(original.payload)
            assertFailsWith<IllegalArgumentException> { record.copy(identity = null) }
            assertFailsWith<IllegalArgumentException> { record.copy(phase = IdentityPhase.AwaitingBackup, recovery = null) }
            assertFailsWith<IllegalArgumentException> { record.copy(phase = IdentityPhase.AwaitingBackup, backup = null) }
            val key = StoredKeyCodec.decodeFromString(fixture.queries.selectByKeyId(record.keyId).executeAsOne().stored_key) as StoredKey.Managed
            val preparation = record.copy(phase = IdentityPhase.Preparing, previous = record, previousKey = key)
            assertFailsWith<IllegalArgumentException> { preparation.copy(previous = preparation) }
            fixture.queries.putIdentityRecord("default", IdentityPhase.Preparing.name, original.payload)
            assertFailsWith<IllegalArgumentException> { fixture.reopen().signingIdentity.state() }
            assertNull(fixture.queries.selectActiveIdentity("default").executeAsOneOrNull())
        }
    }

    private class NativeFixture : PlatformManagedKeyProvider {
        enum class Outcome { Available, Missing, Invalidated, TemporarilyUnavailable }
        var outcome = Outcome.Available
        var failImport = false
        var imports = 0
        private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
        private val handles = mutableMapOf<String, ManagedKey>()
        override suspend fun preflight(requirements: WalletKeyRequirements): KeyUseAuthorizationSupport =
            if (requirements.authorizationPolicy == KeyUseAuthorizationPolicy.None && requirements.protection == WalletKeyProtection.NativeStorage)
                KeyUseAuthorizationSupport.Supported(KeyUseAuthorizationPolicy.None)
            else KeyUseAuthorizationSupport.Unsupported(KeyUseAuthorizationUnsupportedReason.UnsupportedCombination)
        override fun supportsPrivateKeyImport(requirements: WalletKeyRequirements) = requirements.protection == WalletKeyProtection.NativeStorage
        override suspend fun generateManagedKey(request: WalletKeyCreationRequest): ManagedKey = error("This fixture tests imports")
        override suspend fun importManagedKey(request: WalletKeyCreationRequest, material: EncodedKey.Jwk): ManagedKey {
            check(!failImport) { "Fixture import failure" }
            imports++
            val key = runtime.restore(StoredKey.Software(StoredKey.CURRENT_VERSION, request.id, identitySpec, identityUsages, material))
            val descriptor = StoredKey.Managed(StoredKey.CURRENT_VERSION, request.id, identitySpec, identityUsages,
                ProviderId("fixture"), 1, BinaryData(request.nativeAlias.encodeToByteArray()))
            val managed = object : ManagedKey {
                override val storedKey = descriptor
                override val capabilities = key.capabilities
            }
            handles[request.nativeAlias] = managed
            outcome = Outcome.Available
            return managed
        }
        override suspend fun keyFacts(stored: StoredKey.Managed) = PlatformKeyFacts(KeyOrigin.IMPORTED, KeySecurityLevel.SOFTWARE, KeyProtectionLevel.SOFTWARE)
        override fun keyUseAuthorizationPolicy(stored: StoredKey.Managed) = KeyUseAuthorizationPolicy.None
        override suspend fun restoreManagedKey(stored: StoredKey.Managed): PlatformManagedKeyRestoration = when (outcome) {
            Outcome.Available -> PlatformManagedKeyRestoration.Restored(handles.getValue(stored.providerData.toByteArray().decodeToString()), KeyUseAuthorizationPolicy.None)
            Outcome.Missing -> PlatformManagedKeyRestoration.Missing(KeyUseAuthorizationPolicy.None)
            Outcome.Invalidated -> PlatformManagedKeyRestoration.Invalidated(KeyUseAuthorizationPolicy.None)
            Outcome.TemporarilyUnavailable -> throw KeyUseAuthorizationException(KeyUseAuthorizationFailure.ProtectedKeyUnavailable, "Temporarily unavailable")
        }
        override suspend fun deleteUncommittedKey(request: WalletKeyCreationRequest, imported: Boolean) { handles.remove(request.nativeAlias) }
        override suspend fun deleteManagedKey(stored: StoredKey.Managed) { handles.remove(stored.providerData.toByteArray().decodeToString()) }
    }

    private class MemoryCustodian : IdentityKeyCustodian {
        override val id = "test-custodian"
        override val displayName = "Test custodian"
        var imports = 0
        var privateJwk: String? = null
        var returnedJwk: String? = null
        override suspend fun importKey(identity: SigningIdentity, privateKey: EncodedKey.Jwk): IdentityCustodyReceipt {
            imports++
            privateJwk = privateKey.data.toByteArray().decodeToString()
            return IdentityCustodyReceipt("kms/${identity.keyId}", returnedJwk ?: identity.publicJwk)
        }
    }

    private class Fixture(
        configuration: SigningIdentityConfiguration? = null,
        val provider: MemoryRecovery = MemoryRecovery(),
        registry: MobileWalletCredentialRegistry = UnavailableMobileWalletCredentialRegistry,
        private val native: PlatformManagedKeyProvider = NoNative,
        onRegistryChanged: suspend () -> Unit = {},
    ) : AutoCloseable {
        private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        private val db = WalletPersistenceDatabase(driver)
        val queries = db.walletPersistenceQueries
        private val config = MobileWalletConfig(credentialRegistry = registry, onDigitalCredentialRegistryChanged = onRegistryChanged, signingIdentity = configuration ?: SigningIdentityConfiguration(
            recoveryProviders = listOf(provider), authorization = SigningIdentityAuthorization.Explicit(KeyUseAuthorizationPolicy.None)))
        init { WalletPersistenceDatabase.Schema.create(driver) }
        val wallet = reopen()
        fun reopen(signingIdentity: SigningIdentityConfiguration = config.signingIdentity) = createSqlDelightMobileWallet(config.copy(signingIdentity = signingIdentity), ClientIdTrustConfiguration(), db, native, Crypto2DidService, {})
        override fun close() { driver.close() }
    }

    private class MemoryRecovery(override val id: String = "test-memory") : IdentityRecoveryProvider {
        override val displayName = "Test-only memory provider"
        val records = mutableMapOf<String, ByteArray>()
        var corruptReadback = false
        var failure: IdentityProviderFailure? = null
        var receipt = RecoveryReceipt.AcceptedLocally
        var failStore = false
        var available = true
        var availabilityFailure: Exception? = null
        var cancelAfterStore = false
        var cancelRetrieve = false
        var retrieveFailure: IdentityProviderFailure? = null
        override suspend fun availability(): RecoveryAvailability {
            availabilityFailure?.let { throw it }
            return if (available) RecoveryAvailability.Available(RecoveryProtection.ApplicationEncrypted, RecoveryScope.Custom)
                else RecoveryAvailability.Unavailable("Fixture unavailable")
        }
        var listFailure: IdentityProviderFailure? = null
        override suspend fun list(): List<String> {
            listFailure?.let { throw IdentityProviderException(it) }
            return records.keys.toList()
        }
        override suspend fun store(recordId: String, record: IdentityRecoveryData): RecoveryReceipt {
            failure?.let { throw IdentityProviderException(it) }
            check(!failStore)
            val bytes = record.copyBytes()
            check(records[recordId]?.contentEquals(bytes) != false)
            records[recordId] = bytes
            if (cancelAfterStore) throw CancellationException("Interrupted after provider acceptance")
            return receipt
        }
        override suspend fun retrieve(recordId: String): IdentityRecoveryData? {
            retrieveFailure?.let { throw IdentityProviderException(it) }
            if (cancelRetrieve) throw CancellationException("Interrupted retrieval")
            return records[recordId]?.let { IdentityRecoveryData(if (corruptReadback) "corrupt".encodeToByteArray() else it) }
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
