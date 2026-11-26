@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.wallet2.mobile.swiftinterop

import id.walt.credentials.CredentialParser
import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.signatures.sdjwt.SelectivelyDisclosableVerifiableCredential
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.WalletCredentialStore
import id.walt.wallet2.data.WalletDidEntry
import id.walt.wallet2.data.WalletDidStore
import id.walt.wallet2.mobile.MobileWalletStoreAdapter
import id.walt.wallet2.mobile.MobileWalletStoreOwner
import id.walt.wallet2.mobile.credentials.MobileWalletDatabaseKey
import id.walt.wallet2.mobile.credentials.MobileWalletPersistence
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKey
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKeyProvider
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Persistence configuration exposed to the Swift wallet bridge.
 *
 * @property databaseKey Owner of the encrypted local database key.
 * @property credentialStore Optional credential-store override exposed by the Swift facade.
 * @property didStore Optional DID-store override exposed by the Swift facade.
 */
public data class WalletBridgePersistence(
    public val databaseKey: WalletBridgeDatabaseKeyConfiguration = WalletBridgeDatabaseKeyConfiguration.Managed,
    public val credentialStore: WalletBridgeCredentialStore? = null,
    public val didStore: WalletBridgeDidStore? = null,
)

/**
 * Database-key ownership modes exposed to the Swift wallet bridge.
 */
public enum class WalletBridgeDatabaseKeyConfiguration {
    /** Platform-managed encrypted database key. */
    Managed,

    /** Encrypted database key supplied by Swift app code. */
    Provided,
}

/**
 * Database key material returned by a Swift-owned database key provider.
 *
 * @property keyId Stable identifier for the database key.
 * @property material Raw SQLCipher key material.
 */
public data class WalletBridgeDatabaseEncryptionKey(
    public val keyId: String,
    public val material: ByteArray,
)

/**
 * Swift-facing provider for app-supplied encrypted wallet database keys.
 */
public interface WalletBridgeDatabaseEncryptionKeyProvider {
    /**
     * Returns the existing encryption key for [databaseName] or creates one if this provider owns creation.
     */
    public suspend fun getOrCreateKey(walletId: String, databaseName: String): WalletBridgeDatabaseEncryptionKey

    /**
     * Deletes provider-owned key material for [databaseName], if present.
     */
    public suspend fun deleteKey(walletId: String, databaseName: String)
}

/**
 * Credential entry exchanged with Swift custom credential stores.
 *
 * @property id Stable wallet-local credential identifier.
 * @property serializedCredential Raw serialized credential value.
 * @property format Credential format, for example `vc+sd-jwt` or `jwt_vc_json`.
 * @property label Optional user-facing credential label.
 * @property addedAt Optional ISO-8601 timestamp for when the credential was added.
 * @property metadataJson Optional arbitrary metadata as a JSON string.
 */
public data class WalletBridgeStoredCredential(
    public val id: String,
    public val serializedCredential: String,
    public val format: String,
    public val label: String? = null,
    public val addedAt: String? = null,
    public val metadataJson: String? = null,
)

/** Identifies the live host storage behind a Swift credential or DID store adapter. */
public interface WalletBridgeStoreIdentity {
    /** Process-local identity shared by adapters for the same host store. Zero groups value-type stores. */
    public val storageOwnerId: Long
}

/**
 * Swift-facing credential store override.
 */
public interface WalletBridgeCredentialStore {
    /**
     * Returns a stored credential by wallet-local identifier.
     */
    public suspend fun getCredential(id: String): WalletBridgeStoredCredential?

    /**
     * Lists all credentials in this store.
     */
    public suspend fun listCredentials(): List<WalletBridgeStoredCredential>

    /**
     * Adds or replaces a credential entry.
     */
    public suspend fun addCredential(entry: WalletBridgeStoredCredential)

    /**
     * Removes a credential by wallet-local identifier.
     *
     * @return `true` when a credential existed and was removed.
     */
    public suspend fun removeCredential(id: String): Boolean
}

/**
 * DID document entry exchanged with Swift custom DID stores.
 *
 * @property did Stable DID string.
 * @property documentJson Serialized DID document JSON object.
 */
public data class WalletBridgeStoredDid(
    public val did: String,
    public val documentJson: String,
)

/**
 * Swift-facing DID document store override.
 */
public interface WalletBridgeDidStore {
    /**
     * Returns a stored DID document by DID string.
     */
    public suspend fun getDid(did: String): WalletBridgeStoredDid?

    /**
     * Lists all DID documents in this store.
     */
    public suspend fun listDids(): List<WalletBridgeStoredDid>

    /**
     * Adds or replaces a DID document entry.
     */
    public suspend fun addDid(entry: WalletBridgeStoredDid)

    /**
     * Removes a DID document by DID string.
     *
     * @return `true` when a DID existed and was removed.
     */
    public suspend fun removeDid(did: String): Boolean
}

internal fun WalletBridgePersistence.toMobileWalletPersistence(
    databaseKeyProvider: WalletBridgeDatabaseEncryptionKeyProvider?,
): MobileWalletPersistence = MobileWalletPersistence(
    databaseKey = databaseKey.toMobileWalletDatabaseKey(databaseKeyProvider),
    credentialStore = credentialStore?.let(::BridgeCredentialStore),
    didStore = didStore?.let(::BridgeDidStore),
)

private fun WalletBridgeDatabaseKeyConfiguration.toMobileWalletDatabaseKey(
    databaseKeyProvider: WalletBridgeDatabaseEncryptionKeyProvider?,
): MobileWalletDatabaseKey = when (this) {
    WalletBridgeDatabaseKeyConfiguration.Managed ->
        MobileWalletDatabaseKey.Managed

    WalletBridgeDatabaseKeyConfiguration.Provided ->
        MobileWalletDatabaseKey.Provided(
            provider = BridgeDatabaseEncryptionKeyProvider(
                databaseKeyProvider
                    ?: throw IllegalArgumentException("Provided database-key persistence requires a database key provider"),
            )
        )
}

private class BridgeDatabaseEncryptionKeyProvider(
    private val bridgeProvider: WalletBridgeDatabaseEncryptionKeyProvider,
) : DatabaseEncryptionKeyProvider {
    override suspend fun getOrCreateKey(walletId: String, databaseName: String): DatabaseEncryptionKey {
        val key = bridgeProvider.getOrCreateKey(walletId, databaseName)
        return DatabaseEncryptionKey(
            keyId = key.keyId,
            material = key.material,
        )
    }

    override suspend fun deleteKey(walletId: String, databaseName: String) {
        bridgeProvider.deleteKey(walletId, databaseName)
    }
}

private class BridgeCredentialStore(
    private val bridgeStore: WalletBridgeCredentialStore,
) : WalletCredentialStore, MobileWalletStoreAdapter {
    override val storeOwner = MobileWalletStoreOwner(bridgeStore, (bridgeStore as? WalletBridgeStoreIdentity)?.storageOwnerId)

    override suspend fun getCredential(id: String): StoredCredential? =
        bridgeStore.getCredential(id)?.toStoredCredential()

    override suspend fun listCredentials(): Flow<StoredCredential> =
        bridgeStore.listCredentials().map { it.toStoredCredential() }.asFlow()

    override suspend fun addCredential(entry: StoredCredential) {
        bridgeStore.addCredential(entry.toBridgeStoredCredential())
    }

    override suspend fun removeCredential(id: String): Boolean =
        bridgeStore.removeCredential(id)
}

private class BridgeDidStore(
    private val bridgeStore: WalletBridgeDidStore,
) : WalletDidStore, MobileWalletStoreAdapter {
    override val storeOwner = MobileWalletStoreOwner(bridgeStore, (bridgeStore as? WalletBridgeStoreIdentity)?.storageOwnerId)

    override suspend fun getDid(did: String): WalletDidEntry? =
        bridgeStore.getDid(did)?.toWalletDidEntry()

    override suspend fun listDids(): Flow<WalletDidEntry> =
        bridgeStore.listDids().map { it.toWalletDidEntry() }.asFlow()

    override suspend fun addDid(entry: WalletDidEntry) {
        bridgeStore.addDid(entry.toBridgeStoredDid())
    }

    override suspend fun removeDid(did: String): Boolean =
        bridgeStore.removeDid(did)
}

private suspend fun WalletBridgeStoredCredential.toStoredCredential(): StoredCredential {
    val (_, credential) = CredentialParser.detectAndParse(serializedCredential)
    return StoredCredential(
        id = id,
        credential = credential,
        label = label,
        addedAt = addedAt?.let(Instant::parse),
        metadata = metadataJson?.let { Json.decodeFromString(JsonObject.serializer(), it) },
    )
}

private fun StoredCredential.toBridgeStoredCredential() = WalletBridgeStoredCredential(
    id = id,
    serializedCredential = credential.serializedForBridgeStorage(),
    format = credential.format,
    label = label,
    addedAt = addedAt?.toString(),
    metadataJson = metadata?.let { Json.encodeToString(JsonObject.serializer(), it) },
)

private fun DigitalCredential.serializedForBridgeStorage(): String =
    (this as? SelectivelyDisclosableVerifiableCredential)?.signedWithDisclosures?.takeIf { it.isNotBlank() }
        ?: signed?.takeIf { it.isNotBlank() }
        ?: credentialData.toString()

private fun WalletBridgeStoredDid.toWalletDidEntry() = WalletDidEntry(
    did = did,
    document = Json.parseToJsonElement(documentJson).jsonObject,
)

private fun WalletDidEntry.toBridgeStoredDid() = WalletBridgeStoredDid(
    did = did,
    documentJson = Json.encodeToString(JsonObject.serializer(), document),
)
