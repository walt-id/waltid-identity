package id.walt.wallet2.mobile

import id.walt.credentials.CredentialDetectorTypes
import id.walt.credentials.formats.MdocsCredential
import id.walt.credentials.formats.SdJwtCredential
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.WalletCredentialStore
import id.walt.wallet2.mobile.persistence.DatabaseKeySource
import id.walt.wallet2.mobile.persistence.WalletPersistence
import id.walt.wallet2.mobile.digitalcredentials.IosIdentityDocumentRegistry
import id.walt.wallet2.mobile.digitalcredentials.CredentialRegistrationResult
import id.walt.wallet2.mobile.digitalcredentials.CredentialRegistry
import id.walt.wallet2.mobile.digitalcredentials.RegistryEntry
import id.walt.wallet2.mobile.digitalcredentials.DigitalCredentialsConfiguration
import id.walt.wallet2.mobile.digitalcredentials.UnavailableCredentialRegistry
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKey
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKeyProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class WalletPersistenceIntegrationTest {

    @Test
    fun nativeIosRegistriesNeverReadClaimsOrArtworkDuringRegistration() = runTest {
        // Any map lookup or traversal fails, including display-name and image extraction. Both
        // factory-default and explicitly supplied native adapters must select the minimal path.
        val unreadableData = JsonObject(object : AbstractMap<String, JsonElement>() {
            override val entries: Set<Map.Entry<String, JsonElement>>
                get() = error("Native iOS registration must not inspect credential payloads")
        })
        for (registry in listOf(
            null,
            UnavailableCredentialRegistry,
            IosIdentityDocumentRegistry(appGroupIdentifier = null, walletId = "explicit-registry-wallet"),
        )) {
            val credentials = listOf(
                StoredCredential(
                    id = "mdl-1",
                    credential = MdocsCredential(unreadableData, signed = null, docType = "org.iso.18013.5.1.mDL"),
                    metadata = unreadableData,
                ),
                StoredCredential(
                    id = "pid-1",
                    credential = SdJwtCredential(
                        dmtype = CredentialDetectorTypes.SDJWTVCSubType.sdjwtvc,
                        credentialData = unreadableData,
                        signature = null,
                        signed = null,
                    ),
                    metadata = unreadableData,
                ),
            )
            val wallet = WalletFactory().open(registryConfig(registry, credentials))
            try {
                val expected = if (registry === UnavailableCredentialRegistry) "Digital credential registration is unavailable" else "An App Group is required"
                assertEquals(expected, wallet.refreshDigitalCredentialRegistration().reason)
            } finally {
                wallet.deleteLocalData()
            }
        }
    }

    @Test
    fun customIosRegistryStillReceivesFullMdocAndSdJwtRecords() = runTest {
        val published = mutableListOf<RegistryEntry>()
        val registry = object : CredentialRegistry {
            override val capabilities = UnavailableCredentialRegistry.capabilities
            override suspend fun replace(
                registryId: String,
                records: List<RegistryEntry>,
            ): CredentialRegistrationResult {
                published.clear()
                published.addAll(records)
                return CredentialRegistrationResult.Applied(records.size)
            }
        }
        val credentials = listOf(
            StoredCredential(
                id = "mdl-1",
                credential = MdocsCredential(
                    buildJsonObject {
                        put("org.iso.18013.5.1", buildJsonObject { put("given_name", "Ada") })
                    },
                    signed = null,
                    docType = "org.iso.18013.5.1.mDL",
                ),
                label = "Driving licence",
            ),
            StoredCredential(
                id = "pid-1",
                credential = SdJwtCredential(
                    dmtype = CredentialDetectorTypes.SDJWTVCSubType.sdjwtvc,
                    credentialData = buildJsonObject {
                        put("vct", "urn:example:pid")
                        put("given_name", "Ada")
                    },
                    signature = null,
                    signed = null,
                ),
            ),
        )
        val wallet = WalletFactory().open(registryConfig(registry, credentials))
        try {
            assertEquals(2, wallet.refreshDigitalCredentialRegistration().entryCount)
            assertEquals(setOf("mdl-1", "pid-1"), published.map { it.credentialId }.toSet())
            assertTrue(published.all { it.fields.single().valueJson == "\"Ada\"" && it.display.name.isNotBlank() })
        } finally {
            wallet.deleteLocalData()
        }
        assertTrue(published.isEmpty(), "Deleting the wallet must publish an authoritative empty registry")
    }

    @Test
    fun providedIosDatabaseKeyOpensEncryptedStoreAndIsDeleted() = runTest {
        val walletId = "ios-provided-key-${Uuid.random()}"
        val databaseName = "wallet_$walletId"
        val provider = RecordingDatabaseKeyProvider(
            DatabaseEncryptionKey(
                keyId = "provided-key",
                material = ByteArray(32) { index -> (index + 5).toByte() },
            )
        )
        val factory = WalletFactory()
        val config = WalletConfiguration(
            walletId = walletId,
            persistence = WalletPersistence(
                databaseKeySource = DatabaseKeySource.Provided(provider),
            ),
        )

        val wallet = factory.open(config)
        assertEquals(emptyList(), wallet.credentials())

        wallet.close()
        val reopenedWallet = factory.open(config)
        assertEquals(emptyList(), reopenedWallet.credentials())
        assertEquals(listOf("$walletId:$databaseName", "$walletId:$databaseName"), provider.requestedKeys)
        reopenedWallet.deleteLocalData()
        assertEquals(listOf("$walletId:$databaseName"), provider.deletedKeys)
    }

    @Test
    fun customIosCredentialStoreRoutesWithoutReplacingDidOrKeyStores() = runTest {
        val walletId = "ios-custom-credential-store-${Uuid.random()}"
        val databaseName = "wallet_$walletId"
        val credentialStore = RecordingCredentialStore()
        val databaseKeyProvider = RecordingDatabaseKeyProvider(
            DatabaseEncryptionKey(
                keyId = "provided-key",
                material = ByteArray(32) { index -> (index + 7).toByte() },
            )
        )
        val factory = WalletFactory()
        val config = WalletConfiguration(
            walletId = walletId,
            persistence = WalletPersistence(
                databaseKeySource = DatabaseKeySource.Provided(databaseKeyProvider),
                credentialStore = credentialStore,
            ),
        )

        // Kotlin/Native iOS test hosts can report Keychain as unavailable. App-hosted
        // Swift integration tests cover managed Keychain persistence with this store shape.
        val wallet = factory.open(config)
        assertEquals(emptyList(), wallet.credentials())
        assertEquals(listOf("$walletId:$databaseName"), databaseKeyProvider.requestedKeys)
        assertEquals(1, credentialStore.listCredentialsCalls)

        wallet.deleteLocalData()
        assertEquals(listOf("$walletId:$databaseName"), databaseKeyProvider.deletedKeys)
    }

    private fun registryConfig(
        registry: CredentialRegistry?,
        credentials: List<StoredCredential>,
    ): WalletConfiguration = WalletConfiguration(
        walletId = "ios-registry-projection-${Uuid.random()}",
        persistence = WalletPersistence(
            databaseKeySource = DatabaseKeySource.Provided(
                RecordingDatabaseKeyProvider(DatabaseEncryptionKey("provided-key", ByteArray(32) { 11 })),
            ),
            credentialStore = RecordingCredentialStore(credentials),
        ),
        digitalCredentials = DigitalCredentialsConfiguration(registry = registry),
    )

    private class RecordingDatabaseKeyProvider(
        private val key: DatabaseEncryptionKey,
    ) : DatabaseEncryptionKeyProvider {
        val requestedKeys = mutableListOf<String>()
        val deletedKeys = mutableListOf<String>()

        override suspend fun getOrCreateKey(walletId: String, databaseName: String): DatabaseEncryptionKey {
            requestedKeys += "$walletId:$databaseName"
            return key
        }

        override suspend fun deleteKey(walletId: String, databaseName: String) {
            deletedKeys += "$walletId:$databaseName"
        }
    }

    private class RecordingCredentialStore(credentials: List<StoredCredential> = emptyList()) : WalletCredentialStore {
        private val credentials = credentials.associateByTo(mutableMapOf()) { it.id }
        var listCredentialsCalls = 0

        override suspend fun getCredential(id: String): StoredCredential? = credentials[id]

        override suspend fun listCredentials(): Flow<StoredCredential> {
            listCredentialsCalls++
            return credentials.values.asFlow()
        }

        override suspend fun addCredential(entry: StoredCredential) =
            error("This test only verifies credential-store routing")

        override suspend fun removeCredential(id: String): Boolean = credentials.remove(id) != null
    }
}
