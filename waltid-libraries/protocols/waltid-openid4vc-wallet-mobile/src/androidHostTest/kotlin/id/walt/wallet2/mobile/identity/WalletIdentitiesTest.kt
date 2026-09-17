@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.wallet2.mobile.identity

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
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

class WalletIdentitiesTest {
    @Test fun `unavailable provider reason survives alongside a usable recovery route`() = runTest {
        val cloud = MemoryRecovery("cloud").apply { available = false }
        val transfer = MemoryRecovery("transfer")
        Fixture(IdentityConfiguration(recoveryProviders = listOf(cloud, transfer),
            authorization = IdentityAuthorization.Explicit(KeyUseAuthorizationPolicy.None))).use { fixture ->
            val statuses = fixture.wallet.identities.recoveryProviderStatuses()
            assertEquals(listOf("cloud", "transfer"), statuses.map { it.id })
            assertEquals("Fixture unavailable", assertIs<RecoveryAvailability.Unavailable>(statuses[0].availability).reason)
            assertIs<RecoveryAvailability.Available>(statuses[1].availability)
            val options = assertIs<IdentityOptions.Available>(fixture.wallet.identities.creationOptions(IdentityIntent.Recoverable))
            assertTrue((listOf(options.recommended) + options.alternatives).all { it.providerId == "transfer" })
            cloud.available = true
            assertTrue(fixture.wallet.identities.recoveryProviderStatuses().all { it.availability is RecoveryAvailability.Available })
        }
    }

    @Test fun `provider service failure is isolated and redacted but cancellation propagates`() = runTest {
        val broken = MemoryRecovery("broken").apply { availabilityFailure = IllegalStateException("private service details") }
        val available = MemoryRecovery("available")
        Fixture(IdentityConfiguration(recoveryProviders = listOf(broken, available),
            authorization = IdentityAuthorization.Explicit(KeyUseAuthorizationPolicy.None))).use { fixture ->
            val status = fixture.wallet.identities.recoveryProviderStatuses().first()
            assertEquals("The recovery service could not be reached. Try again.",
                assertIs<RecoveryAvailability.Unavailable>(status.availability).reason)
            assertIs<IdentityOptions.Available>(fixture.wallet.identities.creationOptions(IdentityIntent.Recoverable))
            broken.availabilityFailure = CancellationException("cancelled")
            assertFailsWith<CancellationException> { fixture.wallet.identities.recoveryProviderStatuses() }
        }
    }

    @Test fun `activation and reopening publish the registry outside the identity lock`() = runTest {
        lateinit var current: MobileWallet
        var notifications = 0
        Fixture(onRegistryChanged = {
            assertIs<WalletIdentityState.Active>(current.identities.state())
            notifications++
        }).use { fixture ->
            current = fixture.wallet
            val original = assertIs<IdentityOperationResult.Active>(current.identities.initialize()).identity
            assertEquals(1, notifications)
            current = fixture.reopen()
            assertEquals(original, assertIs<IdentityOperationResult.Active>(current.identities.initialize()).identity)
            assertEquals(2, notifications)
        }
    }

    @Test fun `pending setup is not published until resumed and recovery publishes its destination`() = runTest {
        var sourceNotifications = 0
        Fixture(onRegistryChanged = { sourceNotifications++ }).use { source ->
            source.provider.failStore = true
            val option = assertIs<IdentityOptions.Available>(source.wallet.identities.creationOptions(IdentityIntent.Recoverable)).recommended
            val pending = assertIs<IdentityOperationResult.Pending>(source.wallet.identities.create(option))
            assertEquals(0, sourceNotifications)
            source.provider.failStore = false
            val original = assertIs<IdentityOperationResult.Active>(source.wallet.identities.resumePending(pending.identityId)).identity
            assertEquals(1, sourceNotifications)
            var destinationNotifications = 0
            Fixture(provider = source.provider, onRegistryChanged = { destinationNotifications++ }).use { destination ->
                val restore = destination.wallet.identities.restorationOptions(destination.wallet.identities.discoverRecovery().candidates.single()).single()
                val restored = assertIs<IdentityOperationResult.Active>(destination.wallet.identities.restore(restore)).identity
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
            val identity = assertIs<IdentityOperationResult.Active>(fixture.wallet.identities.initialize()).identity
            assertEquals(identity, assertIs<WalletIdentityState.Active>(fixture.wallet.identities.state()).identity)
            assertEquals(false, fixture.wallet.digitalCredentialRegistration.value?.available)
            assertEquals("Registry unavailable", fixture.wallet.digitalCredentialRegistration.value?.reason)
        }
    }

    @Test fun `cancelled submission preserves accepted backup and resumes the same key after restart`() = runTest {
        Fixture().use { fixture ->
            fixture.provider.cancelAfterStore = true
            val option = assertIs<IdentityOptions.Available>(fixture.wallet.identities.creationOptions(IdentityIntent.Recoverable)).recommended
            assertFailsWith<CancellationException> { fixture.wallet.identities.create(option) }
            val pending = assertIs<WalletIdentityState.Pending>(fixture.reopen().identities.state())
            val accepted = fixture.provider.records.getValue(pending.identityId).copyOf()
            val keyId = fixture.queries.selectAll().executeAsList().single().key_id
            fixture.provider.cancelAfterStore = false
            val active = assertIs<IdentityOperationResult.Active>(fixture.reopen().identities.resumePending(pending.identityId)).identity
            assertEquals(keyId, active.keyId)
            assertTrue(accepted.contentEquals(fixture.provider.records.getValue(active.id)), "Retry changed the recovery record")
            assertEquals(1, fixture.queries.selectAll().executeAsList().size)
        }
    }

    @Test fun `missing unavailable and cancelled retrieval cannot create a destination key`() = runTest {
        Fixture().use { source ->
            val original = assertIs<IdentityOperationResult.Active>(source.wallet.identities.create(
                assertIs<IdentityOptions.Available>(source.wallet.identities.creationOptions(IdentityIntent.Recoverable)).recommended)).identity
            Fixture(provider = source.provider).use { destination ->
                val candidate = destination.wallet.identities.discoverRecovery().candidates.single()
                val option = destination.wallet.identities.restorationOptions(candidate).single()
                val record = source.provider.records.remove(original.id)!!
                assertEquals(IdentityFailure.ProviderUnavailable,
                    assertIs<IdentityOperationResult.Failed>(destination.wallet.identities.restore(option)).reason)
                source.provider.records[original.id] = record
                source.provider.available = false
                assertEquals(IdentityFailure.ProviderUnavailable,
                    assertIs<IdentityOperationResult.Failed>(destination.wallet.identities.restore(option)).reason)
                source.provider.available = true
                source.provider.retrieveFailure = IdentityProviderFailure.InteractionRequired
                assertEquals(IdentityFailure.ProviderInteractionRequired,
                    assertIs<IdentityOperationResult.Failed>(destination.wallet.identities.restore(option)).reason)
                source.provider.retrieveFailure = null
                source.provider.cancelRetrieve = true
                assertFailsWith<CancellationException> { destination.wallet.identities.restore(option) }
                assertEquals(WalletIdentityState.Absent, destination.reopen().identities.state())
                assertTrue(destination.queries.selectAll().executeAsList().isEmpty())
                source.provider.cancelRetrieve = false
                val restarted = destination.reopen()
                val retry = restarted.identities.restorationOptions(restarted.identities.discoverRecovery().candidates.single()).single()
                assertEquals(original.publicJwk, assertIs<IdentityOperationResult.Active>(restarted.identities.restore(retry)).identity.publicJwk)
            }
        }
    }

    @Test fun `restart cleans interrupted restoration before allowing an explicit retry`() = runTest {
        Fixture().use { source ->
            val original = assertIs<IdentityOperationResult.Active>(source.wallet.identities.create(
                assertIs<IdentityOptions.Available>(source.wallet.identities.creationOptions(IdentityIntent.Recoverable)).recommended)).identity
            Fixture(provider = source.provider).use { destination ->
                val option = destination.wallet.identities.restorationOptions(destination.wallet.identities.discoverRecovery().candidates.single()).single()
                assertIs<IdentityOperationResult.Active>(destination.wallet.identities.restore(option))
                // Emulate process death after key import/journaling but before active binding was committed.
                val stored = recordJson.decodeFromString<IdentityRecord>(destination.queries.selectIdentityRecord("default").executeAsOne().payload)
                destination.queries.putIdentityRecord("default", IdentityPhase.Preparing.name,
                    recordJson.encodeToString(stored.copy(phase = IdentityPhase.Preparing)))
                val restarted = destination.reopen()
                assertIs<WalletIdentityState.Pending>(restarted.identities.state())
                assertIs<IdentityOperationResult.Failed>(restarted.identities.resumePending(original.id))
                assertEquals(WalletIdentityState.Absent, restarted.identities.state())
                assertTrue(destination.queries.selectAll().executeAsList().isEmpty())
                assertNotNull(source.provider.records[original.id])
                val retry = restarted.identities.restorationOptions(restarted.identities.discoverRecovery().candidates.single()).single()
                assertEquals(original.publicJwk, assertIs<IdentityOperationResult.Active>(restarted.identities.restore(retry)).identity.publicJwk)
            }
        }
    }
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
                val candidate = destination.wallet.identities.discoverRecovery().candidates.single()
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
                val option = destination.wallet.identities.restorationOptions(destination.wallet.identities.discoverRecovery().candidates.single()).single()
                assertEquals(created.publicJwk, assertIs<IdentityOperationResult.Active>(destination.wallet.identities.restore(option)).identity.publicJwk)
            }
        }
    }

    @Test fun `corrupted or changed recovery record cannot create a destination key`() = runTest {
        Fixture().use { original ->
            val created = assertIs<IdentityOperationResult.Active>(original.wallet.identities.create(
                assertIs<IdentityOptions.Available>(original.wallet.identities.creationOptions(IdentityIntent.Recoverable)).recommended)).identity
            Fixture(provider = original.provider).use { destination ->
                val candidate = destination.wallet.identities.discoverRecovery().candidates.single()
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
                fixture.wallet.identities.discoverRecovery().candidates.single()))
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
                val candidate = destination.wallet.identities.discoverRecovery().candidates.single()
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

    @Test fun `restoration preserves recorded minimums instead of weakening them to destination defaults`() = runTest {
        Fixture().use { source ->
            val option = assertIs<IdentityOptions.Available>(source.wallet.identities.creationOptions(IdentityIntent.Recoverable)).recommended
            val original = assertIs<IdentityOperationResult.Active>(source.wallet.identities.create(option)).identity
            val bytes = source.provider.records.getValue(original.id)
            val record = recordJson.decodeFromString<RecoveryRecord>(bytes.decodeToString())
            assertEquals(IdentityKeyStorage.EncryptedDatabase, record.constraints.storage)
            assertEquals(KeyUseAuthorizationPolicy.None, record.constraints.authorization)
            for (restricted in listOf(
                record.constraints.copy(storage = IdentityKeyStorage.Hardware),
                record.constraints.copy(authorization = KeyUseAuthorizationPolicy.BiometricCurrentSet),
            )) {
                source.provider.records[original.id] = record.copy(constraints = restricted).encode()
                Fixture(provider = source.provider).use { destination ->
                    val candidate = destination.wallet.identities.discoverRecovery().candidates.single()
                    assertTrue(destination.wallet.identities.restorationOptions(candidate).isEmpty())
                    assertTrue(destination.queries.selectAll().executeAsList().isEmpty())
                }
            }
        }
    }

    @Test fun `provider failures remain actionable after restarting pending setup`() = runTest {
        val cases = mapOf(
            IdentityProviderFailure.TemporarilyUnavailable to IdentityFailure.ProviderUnavailable,
            IdentityProviderFailure.InteractionRequired to IdentityFailure.ProviderInteractionRequired,
            IdentityProviderFailure.Rejected to IdentityFailure.ProviderRejected,
            IdentityProviderFailure.Conflict to IdentityFailure.ProviderConflict,
            IdentityProviderFailure.ConfirmationPending to IdentityFailure.ProviderConfirmationPending,
        )
        for ((cause, expected) in cases) Fixture().use { fixture ->
            fixture.provider.failure = cause
            val option = assertIs<IdentityOptions.Available>(fixture.wallet.identities.creationOptions(IdentityIntent.Recoverable)).recommended
            val pending = assertIs<IdentityOperationResult.Pending>(fixture.wallet.identities.create(option))
            assertEquals(expected, pending.reason)
            assertEquals(expected, assertIs<WalletIdentityState.Pending>(fixture.reopen().identities.state()).reason)
            assertNull(fixture.queries.selectActiveIdentity("default").executeAsOneOrNull())
            fixture.provider.failure = null
            assertIs<IdentityOperationResult.Active>(fixture.wallet.identities.resumePending(pending.identityId))
        }
    }

    @Test fun `custody preserves local identity and recovery state and persists one verified reference`() = runTest {
        val custodian = MemoryCustodian()
        Fixture(IdentityConfiguration(keyCustodians = listOf(custodian),
            authorization = IdentityAuthorization.Explicit(KeyUseAuthorizationPolicy.None))).use { fixture ->
            val original = assertIs<IdentityOperationResult.Active>(fixture.wallet.identities.initialize()).identity
            val option = fixture.wallet.identities.custodyOptions(original.id).single()
            repeat(2) { assertIs<IdentityCustodyResult.Imported>(fixture.wallet.identities.transferToCustody(option)) }
            val active = assertIs<WalletIdentityState.Active>(fixture.reopen().identities.state()).identity
            assertEquals(original.did, active.did)
            assertEquals(original.publicJwk, active.publicJwk)
            assertEquals(original.recovery, active.recovery)
            assertEquals(listOf(IdentityCustodyReference(custodian.id, "kms/${original.keyId}")), active.custody)
            assertNotNull(SqlDelightKeyStore(NoNative, fixture.queries, "default").getDefaultKeyMaterial())
            assertEquals(2, custodian.imports)
            assertTrue(custodian.privateJwk!!.contains("\"d\""))
            assertEquals(IdentityFailure.StaleOption,
                assertIs<IdentityCustodyResult.Failed>(fixture.reopen().identities.transferToCustody(option)).reason)
        }
    }

    @Test fun `custody conflicts never record verified custody and restricted identities never offer export`() = runTest {
        val custodian = MemoryCustodian()
        val config = IdentityConfiguration(keyCustodians = listOf(custodian),
            authorization = IdentityAuthorization.Explicit(KeyUseAuthorizationPolicy.None))
        Fixture(config).use { fixture ->
            val original = assertIs<IdentityOperationResult.Active>(fixture.wallet.identities.initialize()).identity
            Fixture().use { other ->
                custodian.returnedJwk = assertIs<IdentityOperationResult.Active>(other.wallet.identities.initialize()).identity.publicJwk
            }
            val option = fixture.wallet.identities.custodyOptions(original.id).single()
            assertEquals(IdentityFailure.ProviderConflict,
                assertIs<IdentityCustodyResult.Failed>(fixture.wallet.identities.transferToCustody(option)).reason)
            assertTrue(assertIs<WalletIdentityState.Active>(fixture.wallet.identities.state()).identity.custody.isEmpty())
            val restricted = fixture.reopen(config.copy(policy = IdentityKeyPolicy.DeviceBound))
            assertTrue(restricted.identities.custodyOptions(original.id).isEmpty())
        }
        Fixture(config.copy(policy = IdentityKeyPolicy.DeviceBound)).use { fixture ->
            val original = assertIs<IdentityOperationResult.Active>(fixture.wallet.identities.initialize()).identity
            assertTrue(fixture.wallet.identities.custodyOptions(original.id).isEmpty())
            assertTrue(fixture.reopen(config).identities.custodyOptions(original.id).isEmpty())
        }
    }

    @Test fun `issuance restrictions require retained identity policy and reject a different key`() = runTest {
        for (policy in listOf(IdentityKeyPolicy.GeneralPurpose, IdentityKeyPolicy.DeviceBound)) {
            Fixture(IdentityConfiguration(policy = policy,
                authorization = IdentityAuthorization.Explicit(KeyUseAuthorizationPolicy.None))).use { fixture ->
                val identity = assertIs<IdentityOperationResult.Active>(fixture.wallet.identities.initialize()).identity
                val reopened = fixture.reopen().identities
                reopened.requireKeyPolicy(identity.keyId, IdentityKeyPolicy.GeneralPurpose)
                if (policy == IdentityKeyPolicy.DeviceBound) reopened.requireKeyPolicy(identity.keyId, policy)
                else assertFailsWith<IllegalArgumentException> { reopened.requireKeyPolicy(identity.keyId, IdentityKeyPolicy.DeviceBound) }
                assertFailsWith<IllegalArgumentException> { reopened.requireKeyPolicy(identity.keyId, IdentityKeyPolicy.HardwareGenerated) }
                assertFailsWith<IllegalArgumentException> { reopened.requireKeyPolicy("different", IdentityKeyPolicy.DeviceBound) }
            }
        }
    }


    @Test fun `discovery survives one provider list outage`() = runTest {
        val broken = MemoryRecovery("broken").apply { listFailure = IdentityProviderFailure.TemporarilyUnavailable }
        val healthy = MemoryRecovery("healthy")
        healthy.records["recoverable-identity"] = byteArrayOf(1)
        Fixture(IdentityConfiguration(recoveryProviders = listOf(broken, healthy),
            authorization = IdentityAuthorization.Explicit(KeyUseAuthorizationPolicy.None))).use { fixture ->
            val discovery = fixture.wallet.identities.discoverRecovery()
            assertEquals(listOf("healthy"), discovery.candidates.map { it.reference.providerId })
            assertEquals(listOf(IdentityRecoveryProviderFailure("broken", broken.displayName, IdentityFailure.ProviderUnavailable)), discovery.failures)
            broken.availabilityFailure = CancellationException("cancelled")
            assertFailsWith<CancellationException> { fixture.wallet.identities.discoverRecovery() }
        }
    }

    @Test fun `execution availability errors are safe and do not activate a key`() = runTest {
        Fixture().use { fixture ->
            val manager = fixture.wallet.identities
            val option = assertIs<IdentityOptions.Available>(manager.creationOptions(IdentityIntent.Recoverable)).recommended
            fixture.provider.availabilityFailure = IllegalStateException("private details")
            assertEquals(IdentityFailure.ProviderUnavailable, assertIs<IdentityOperationResult.Failed>(manager.create(option)).reason)
            assertEquals(WalletIdentityState.Absent, manager.state())
            fixture.provider.availabilityFailure = null
            val identity = assertIs<IdentityOperationResult.Active>(manager.create(option)).identity
            val backup = manager.backupOptions(identity.id).single()
            fixture.provider.availabilityFailure = IllegalStateException("private details")
            assertEquals(IdentityFailure.ProviderUnavailable, assertIs<IdentityOperationResult.Failed>(manager.backup(backup)).reason)
            assertEquals(identity, assertIs<WalletIdentityState.Active>(manager.state()).identity)
        }
    }

    @Test fun `candidate retrieval failures are typed and cancellation propagates`() = runTest {
        Fixture().use { fixture ->
            val manager = fixture.wallet.identities
            manager.create(assertIs<IdentityOptions.Available>(manager.creationOptions(IdentityIntent.Recoverable)).recommended)
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
            val option = assertIs<IdentityOptions.Available>(fixture.wallet.identities.creationOptions(IdentityIntent.Recoverable)).recommended
            assertEquals(IdentityFailure.ProviderInteractionRequired,
                assertIs<IdentityOperationResult.Pending>(fixture.wallet.identities.create(option)).reason)
            assertEquals(IdentityFailure.ProviderInteractionRequired,
                assertIs<IdentityOperationResult.Pending>(fixture.reopen().identities.initialize()).reason)
        }
    }

    private class MemoryCustodian : IdentityKeyCustodian {
        override val id = "test-custodian"
        override val displayName = "Test custodian"
        var imports = 0
        var privateJwk: String? = null
        var returnedJwk: String? = null
        override suspend fun importKey(identity: WalletIdentity, privateKey: EncodedKey.Jwk): IdentityCustodyReceipt {
            imports++
            privateJwk = privateKey.data.toByteArray().decodeToString()
            return IdentityCustodyReceipt("kms/${identity.keyId}", returnedJwk ?: identity.publicJwk)
        }
    }

    private class Fixture(
        configuration: IdentityConfiguration? = null,
        val provider: MemoryRecovery = MemoryRecovery(),
        registry: MobileWalletCredentialRegistry = UnavailableMobileWalletCredentialRegistry,
        onRegistryChanged: suspend () -> Unit = {},
    ) : AutoCloseable {
        private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        private val db = WalletPersistenceDatabase(driver)
        val queries = db.walletPersistenceQueries
        private val config = MobileWalletConfig(credentialRegistry = registry, onDigitalCredentialRegistryChanged = onRegistryChanged, identity = configuration ?: IdentityConfiguration(
            recoveryProviders = listOf(provider), authorization = IdentityAuthorization.Explicit(KeyUseAuthorizationPolicy.None)))
        init { WalletPersistenceDatabase.Schema.create(driver) }
        val wallet = reopen()
        fun reopen(identity: IdentityConfiguration = config.identity) = createSqlDelightMobileWallet(config.copy(identity = identity), ClientIdTrustConfiguration(), db, NoNative, Crypto2DidService, {})
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
