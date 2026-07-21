package id.walt.wallet2.mobile.swiftinterop

import id.walt.credentials.CredentialParser
import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.signatures.sdjwt.SelectivelyDisclosableVerifiableCredential
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.KeySerialization
import id.walt.crypto.keys.KeyType
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.WalletCredentialStore
import id.walt.wallet2.data.WalletDidEntry
import id.walt.wallet2.data.WalletDidStore
import id.walt.wallet2.data.WalletKeyInfo
import id.walt.wallet2.data.WalletKeyStore
import id.walt.wallet2.data.WalletX509TrustConfig
import id.walt.wallet2.mobile.MobileWalletConfig
import id.walt.wallet2.mobile.MobileWalletCrossProcessAccess
import id.walt.wallet2.mobile.MobileWalletDatabaseKey
import id.walt.wallet2.mobile.MobileWalletKeys
import id.walt.wallet2.mobile.MobileWalletKeyType
import id.walt.wallet2.mobile.MobileWalletPersistence
import id.walt.wallet2.mobile.MobileWalletStores
import id.walt.wallet2.mobile.MobileWalletTransactionDataProfile
import id.walt.wallet2.mobile.WalletAttestationConfig
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKey
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKeyProvider
import id.walt.wallet2.persistence.encryption.WalletPersistenceException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.time.Instant

/**
 * Configuration used when creating an iOS [WalletSdkBridge].
 *
 * @property walletId Stable wallet identifier used for database naming and persisted wallet state.
 * @property defaultKeyType Key type used by wallet bootstrap when no key type override is supplied.
 * @property persistence Wallet-local persistence configuration.
 * @property databaseKeyProvider Swift-owned database key provider used when [persistence] uses
 * [WalletBridgeDatabaseKeyConfiguration.Provided].
 * @property attestation Optional client-attestation configuration for issuers that require it.
 * @property requestObjectTrustAnchorPemCertificates Wallet-controlled PEM trust anchors for
 * signed OID4VP Request Objects.
 * @property requestObjectEnableSystemTrustAnchors Retained for source compatibility. iOS system
 * trust anchors are not supported for Request Object validation and `true` is rejected.
 * @property requestObjectAudience Expected Request Object audience.
 * @property transactionDataProfiles Transaction data profiles this wallet accepts.
 * @property appGroupIdentifier Shared container used by the app and document-provider extension.
 * @property keychainAccessGroup Shared Keychain access group used for database and signing keys.
 */
public data class WalletBridgeConfiguration(
    public val walletId: String = "default",
    public val defaultKeyType: MobileWalletKeyType = MobileWalletKeyType.secp256r1,
    public val persistence: WalletBridgePersistence = WalletBridgePersistence(),
    public val databaseKeyProvider: WalletBridgeDatabaseEncryptionKeyProvider? = null,
    public val attestation: WalletAttestationConfig? = null,
    public val requestObjectTrustAnchorPemCertificates: List<String> = emptyList(),
    public val requestObjectEnableSystemTrustAnchors: Boolean = false,
    public val requestObjectAudience: String = "https://self-issued.me/v2",
    public val transactionDataProfiles: List<MobileWalletTransactionDataProfile> = emptyList(),
    public val appGroupIdentifier: String? = null,
    public val keychainAccessGroup: String? = null,
)

internal fun WalletBridgeConfiguration.toMobileWalletConfig(): MobileWalletConfig {
    require(!requestObjectEnableSystemTrustAnchors) {
        "iOS system trust anchors are not supported for OID4VP Request Object validation"
    }
    require((appGroupIdentifier == null) == (keychainAccessGroup == null)) {
        "App Group and Keychain access group must be configured together"
    }
    val x509Trust = if (requestObjectTrustAnchorPemCertificates.isNotEmpty() || requestObjectEnableSystemTrustAnchors) {
        WalletX509TrustConfig(
            trustAnchorPemCertificates = requestObjectTrustAnchorPemCertificates,
            enableSystemTrustAnchors = requestObjectEnableSystemTrustAnchors,
        )
    } else {
        null
    }
    return MobileWalletConfig(
        walletId = walletId,
        defaultKeyType = defaultKeyType,
        attestationConfig = attestation,
        persistence = persistence.toMobileWalletPersistence(databaseKeyProvider),
        requestObjectX509Trust = x509Trust,
        requestObjectAudience = requestObjectAudience,
        transactionDataProfiles = transactionDataProfiles,
        crossProcessAccess = appGroupIdentifier?.let { appGroup ->
            MobileWalletCrossProcessAccess(
                appGroupIdentifier = appGroup,
                keychainAccessGroup = requireNotNull(keychainAccessGroup),
            )
        },
    )
}

/**
 * Persistence configuration exposed to the Swift wallet bridge.
 *
 * @property databaseKey Owner of the encrypted local database key.
 * @property stores Optional store overrides exposed by the Swift facade.
 */
public data class WalletBridgePersistence(
    public val databaseKey: WalletBridgeDatabaseKeyConfiguration = WalletBridgeDatabaseKeyConfiguration.Managed,
    public val stores: WalletBridgeStores = WalletBridgeStores(),
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
 * Optional Swift-provided store overrides exposed to the bridge.
 *
 * @property credentials Optional Swift-provided credential store.
 * @property dids Optional Swift-provided DID document store.
 * @property keys Optional atomic Swift-provided key store and key generator.
 */
public data class WalletBridgeStores(
    public val credentials: WalletBridgeCredentialStore? = null,
    public val dids: WalletBridgeDidStore? = null,
    public val keys: WalletBridgeKeys? = null,
)

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
 */
public data class WalletBridgeStoredCredential(
    public val id: String,
    public val serializedCredential: String,
    public val format: String,
    public val label: String? = null,
    public val addedAt: String? = null,
)

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

/**
 * Key metadata exchanged with Swift custom key stores.
 *
 * @property keyId Stable wallet-local key identifier.
 * @property keyType Wallet key type name.
 * @property algorithm Optional signing algorithm label supplied by Swift.
 */
public data class WalletBridgeKeyInfo(
    public val keyId: String,
    public val keyType: String,
    public val algorithm: String? = null,
)

/**
 * Serialized signing key exchanged with Swift custom key stores.
 *
 * @property keyId Stable wallet-local key identifier.
 * @property keyType Wallet key type name.
 * @property algorithm Optional signing algorithm label supplied by Swift.
 * @property serializedKeyJson walt.id serialized key JSON payload.
 */
public data class WalletBridgeStoredKey(
    public val keyId: String,
    public val keyType: String,
    public val algorithm: String? = null,
    public val serializedKeyJson: String,
) {
    /**
     * Text representation that redacts serialized key material.
     */
    public override fun toString(): String =
        "WalletBridgeStoredKey(keyId=$keyId, keyType=$keyType, algorithm=$algorithm, serializedKeyJson=<redacted>)"
}

/**
 * Atomic Swift-facing key store and generator override.
 *
 * KMP requires the store and generator together so generated signing keys are persisted into the same
 * app-owned key domain.
 *
 * @property store Swift-provided signing-key store.
 * @property generate Swift-provided signing-key generator.
 */
public data class WalletBridgeKeys(
    public val store: WalletBridgeKeyStore,
    public val generate: WalletBridgeKeyGenerator,
)

/**
 * Swift-facing signing-key store override.
 */
public interface WalletBridgeKeyStore {
    /**
     * Returns a serialized signing key by wallet-local identifier.
     */
    public suspend fun getKey(keyId: String): WalletBridgeStoredKey?

    /**
     * Lists stored signing-key metadata.
     */
    public suspend fun listKeys(): List<WalletBridgeKeyInfo>

    /**
     * Adds or replaces a serialized signing key entry.
     *
     * @return Stable wallet-local key identifier for the stored key.
     */
    public suspend fun addKey(entry: WalletBridgeStoredKey): String

    /**
     * Removes a signing key by wallet-local identifier.
     *
     * @return `true` when a key existed and was removed.
     */
    public suspend fun removeKey(keyId: String): Boolean
}

/**
 * Swift-facing signing-key generator override.
 */
public interface WalletBridgeKeyGenerator {
    /**
     * Generates a serialized signing key for [keyType].
     */
    public suspend fun generateKey(keyType: MobileWalletKeyType): WalletBridgeStoredKey
}

private fun WalletBridgePersistence.toMobileWalletPersistence(
    databaseKeyProvider: WalletBridgeDatabaseEncryptionKeyProvider?,
): MobileWalletPersistence = MobileWalletPersistence(
    databaseKey = databaseKey.toMobileWalletDatabaseKey(databaseKeyProvider),
    stores = MobileWalletStores(
        credentials = stores.credentials?.let(::BridgeCredentialStore),
        dids = stores.dids?.let(::BridgeDidStore),
        keys = stores.keys?.toMobileWalletKeys(),
    ),
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
) : WalletCredentialStore {
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
) : WalletDidStore {
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

private class BridgeKeyStore(
    private val bridgeStore: WalletBridgeKeyStore,
) : WalletKeyStore {
    override suspend fun getKey(keyId: String): Key? =
        bridgeStore.getKey(keyId)?.toKey()

    override suspend fun listKeys(): Flow<WalletKeyInfo> =
        bridgeStore.listKeys().map { it.toWalletKeyInfo() }.asFlow()

    override suspend fun addKey(key: Key): String =
        bridgeStore.addKey(key.toBridgeStoredKey())

    override suspend fun removeKey(keyId: String): Boolean =
        bridgeStore.removeKey(keyId)
}

private fun WalletBridgeKeys.toMobileWalletKeys() = MobileWalletKeys(
    store = BridgeKeyStore(store),
    generate = { keyType -> generate.generateKey(keyType.toMobileWalletKeyType()).toKey() },
)

private suspend fun WalletBridgeStoredCredential.toStoredCredential(): StoredCredential {
    val (_, credential) = CredentialParser.detectAndParse(serializedCredential)
    return StoredCredential(
        id = id,
        credential = credential,
        label = label,
        addedAt = addedAt?.let(Instant::parse),
    )
}

private fun StoredCredential.toBridgeStoredCredential() = WalletBridgeStoredCredential(
    id = id,
    serializedCredential = credential.serializedForBridgeStorage(),
    format = credential.format,
    label = label,
    addedAt = addedAt?.toString(),
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

private fun WalletBridgeKeyInfo.toWalletKeyInfo() = WalletKeyInfo(
    keyId = keyId,
    keyType = keyType,
    algorithm = algorithm,
)

private suspend fun WalletBridgeStoredKey.toKey(): Key =
    KeyManager.resolveSerializedKey(serializedKeyJson)

private suspend fun Key.toBridgeStoredKey() = WalletBridgeStoredKey(
    keyId = getKeyId(),
    keyType = keyType.name,
    algorithm = null,
    serializedKeyJson = KeySerialization.serializeKey(this),
)

private fun KeyType.toMobileWalletKeyType(): MobileWalletKeyType = when (this) {
    KeyType.Ed25519 -> MobileWalletKeyType.Ed25519
    KeyType.secp256k1 -> MobileWalletKeyType.secp256k1
    KeyType.secp256r1 -> MobileWalletKeyType.secp256r1
    KeyType.secp384r1 -> MobileWalletKeyType.secp384r1
    KeyType.secp521r1 -> MobileWalletKeyType.secp521r1
    KeyType.RSA -> MobileWalletKeyType.RSA
    KeyType.RSA3072 -> MobileWalletKeyType.RSA3072
    KeyType.RSA4096 -> MobileWalletKeyType.RSA4096
}

/**
 * Coarse error category for Swift bridge failures.
 */
@Serializable
public enum class WalletBridgeErrorCategory {
    /** Input supplied by the caller is invalid. */
    invalidInput,

    /** Network communication failed. */
    network,

    /** Issuer-side processing failed. */
    issuer,

    /** Verifier-side processing failed. */
    verifier,

    /** Wallet storage access failed. */
    storage,

    /** Cryptographic operation failed. */
    crypto,

    /** Requested credential could not be found. */
    credentialNotFound,

    /** The operation was cancelled. */
    cancelled,

    /** Unexpected wallet failure that does not fit a narrower category. */
    internalFailure,
}

/**
 * Serializable error returned to Swift callers when a bridge operation fails.
 *
 * @property category Coarse failure category.
 * @property message Human-readable failure message.
 * @property causeClass Kotlin exception class name when available.
 */
@Serializable
public data class WalletBridgeError(
    val category: WalletBridgeErrorCategory,
    val message: String,
    val causeClass: String? = null,
) {
    internal companion object {
        fun fromThrowable(throwable: Throwable): WalletBridgeError {
            val category = when (throwable) {
                is CancellationException -> WalletBridgeErrorCategory.cancelled
                is IllegalArgumentException -> WalletBridgeErrorCategory.invalidInput
                is WalletPersistenceException -> WalletBridgeErrorCategory.storage
                else -> WalletBridgeErrorCategory.internalFailure
            }

            return WalletBridgeError(
                category = category,
                message = throwable.message ?: throwable::class.simpleName ?: "Unknown wallet error",
                causeClass = throwable::class.simpleName,
            )
        }
    }
}

/**
 * Result wrapper used by Swift bridge operations.
 */
public sealed interface WalletBridgeResult<out T> {
    /**
     * Successful bridge operation result.
     *
     * @property value Value returned by the wallet operation.
     */
    public data class Success<T>(public val value: T) : WalletBridgeResult<T>

    /**
     * Failed bridge operation result.
     *
     * @property error Structured bridge error returned to Swift callers.
     */
    public data class Failure(public val error: WalletBridgeError) : WalletBridgeResult<Nothing>
}
