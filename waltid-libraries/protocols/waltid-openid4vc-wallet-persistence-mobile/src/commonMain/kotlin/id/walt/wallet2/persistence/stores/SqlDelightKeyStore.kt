package id.walt.wallet2.persistence.stores

import id.walt.crypto.keys.Key
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.ManagedKey
import id.walt.crypto2.keys.StorableKey
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.keys.Key as StoredKeyMaterial
import id.walt.crypto2.keys.KeyEncodingFormat
import id.walt.crypto2.providers.CryptoOperation
import id.walt.crypto2.providers.CryptoRequirement
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.crypto2.serialization.StoredKeyCodec
import id.walt.wallet2.data.WalletKeyInfo
import id.walt.wallet2.data.WalletKeyStore
import id.walt.wallet2.data.WalletKeyStoreEntry
import id.walt.wallet2.data.WalletKeyUsageUnsupportedException
import id.walt.wallet2.persistence.db.WalletPersistenceQueries
import id.walt.wallet2.persistence.keys.PlatformManagedKeyProvider
import id.walt.wallet2.persistence.keys.MobileWalletKeyStore
import id.walt.wallet2.persistence.keys.KeyUseAuthorizationException
import id.walt.wallet2.persistence.keys.KeyUseAuthorizationFailure
import id.walt.wallet2.persistence.keys.KeyUseAuthorizationPolicy
import id.walt.wallet2.persistence.keys.KeyUseAuthorizationUnsupportedReason
import id.walt.wallet2.persistence.keys.WalletKeyCreationRequest
import id.walt.wallet2.persistence.keys.WalletKeyRequirements
import id.walt.wallet2.persistence.keys.KeyUseAuthorizationSupport
import id.walt.wallet2.persistence.keys.PlatformManagedKeyRestoration
import id.walt.wallet2.persistence.keys.toAuthorizationFailure
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/**
 * Wallet key store backed by SQLDelight.
 *
 * Every row persists the versioned [StoredKey] descriptor required to restore the key. Managed keys retain private
 * material in the native platform store; software keys retain their encoded material in the descriptor. This is a
 * fresh-schema store and deliberately does not read, repair, or write legacy key references.
 */
public class SqlDelightKeyStore(
    private val managedKeyProvider: PlatformManagedKeyProvider,
    private val queries: WalletPersistenceQueries,
) : MobileWalletKeyStore {
    private val softwareRuntime = CryptoRuntime(defaultSoftwareKeyProviders())

    /** Legacy key material is not supported by the mobile store. */
    override suspend fun getKey(keyId: String): Key? = null

    /** Restores a persisted storable key with the requested usages. */
    override suspend fun getCrypto2Key(keyId: String, usages: Set<KeyUsage>): StoredKeyMaterial? =
        queries.selectByKeyId(keyId).executeAsOneOrNull()?.let { ref ->
            restoreStoredKey(decodeStoredKey(ref.key_id, ref.stored_key)).also { key ->
                key?.let { restored ->
                    if (!usages.all(restored.usages::contains)) {
                        throw WalletKeyUsageUnsupportedException("Mobile key does not permit requested usages")
                    }
                }
            }
        }

    /** Restores a persisted key as a wallet key-store entry. */
    override suspend fun getKeyMaterial(keyId: String, usages: Set<KeyUsage>): WalletKeyStoreEntry? =
        getCrypto2Key(keyId, usages)?.let { WalletKeyStoreEntry(keyId, legacyKey = null, crypto2Key = it) }

    /** Lists the identifiers and specifications of all persisted keys. */
    override suspend fun listKeys(): Flow<WalletKeyInfo> = flow {
        queries.selectAll().executeAsList().forEach { ref ->
            val stored = decodeStoredKey(ref.key_id, ref.stored_key)
            emit(WalletKeyInfo(keyId = stored.id.value, keyType = stored.spec.toString()))
        }
    }

    override suspend fun keyUseAuthorizationPolicy(keyId: String): KeyUseAuthorizationPolicy? =
        queries.selectByKeyId(keyId).executeAsOneOrNull()?.let { ref ->
            when (val stored = decodeStoredKey(ref.key_id, ref.stored_key)) {
                is StoredKey.Managed -> managedKeyProvider.keyUseAuthorizationPolicy(stored)
                is StoredKey.Software -> KeyUseAuthorizationPolicy.None
            }
        }

    /** Legacy keys must be converted to a storable key before they are persisted. */
    override suspend fun addKey(key: Key): String =
        throw UnsupportedOperationException("Mobile key storage supports storable keys only")

    /** Checks whether the wallet can satisfy the request using a managed key or an allowed software key. */
    public suspend fun preflight(requirements: WalletKeyRequirements): KeyUseAuthorizationSupport =
        managedKeyProvider.preflight(requirements).let { managedSupport ->
            if (managedSupport is KeyUseAuthorizationSupport.Supported) {
                managedSupport
            } else if (requirements.authorizationPolicy !is KeyUseAuthorizationPolicy.None) {
                managedSupport
            } else if (supportsSoftware(requirements)) {
                KeyUseAuthorizationSupport.Supported(requirements.authorizationPolicy)
            } else {
                KeyUseAuthorizationSupport.Unsupported(
                    KeyUseAuthorizationUnsupportedReason.UnsupportedCombination,
                )
            }
        }

    /** Generates and persists either a software or managed Crypto2 key. */
    public suspend fun generateKey(request: WalletKeyCreationRequest): StoredKeyMaterial {
        require(queries.selectByKeyId(request.id.value).executeAsOneOrNull() == null) {
            "Mobile key already exists: ${request.id.value}"
        }
        val managedSupport = managedKeyProvider.preflight(request.requirements)
        val key = when (managedSupport) {
            is KeyUseAuthorizationSupport.Supported ->
                managedKeyProvider.generateManagedKey(request)

            is KeyUseAuthorizationSupport.Unsupported -> {
                if (request.requirements.authorizationPolicy !is KeyUseAuthorizationPolicy.None) {
                    throw KeyUseAuthorizationException(
                        failure = managedSupport.reason.toAuthorizationFailure(),
                        message = "The platform cannot enforce ${request.requirements.authorizationPolicy} for ${request.requirements.spec}",
                    )
                }
                if (!supportsSoftware(request.requirements)) {
                    throw KeyUseAuthorizationException(
                        failure = KeyUseAuthorizationFailure.UnsupportedCombination,
                        message = "The wallet cannot satisfy ${request.requirements.spec} with ${request.requirements.usages}",
                    )
                }
                softwareRuntime.generateSoftwareKey(
                    GenerateSoftwareKeyRequest(
                        id = request.id,
                        spec = request.requirements.spec,
                        usages = request.requirements.usages,
                    )
                )
            }
        }
        try {
            addCrypto2Key(key)
        } catch (cause: Throwable) {
            if (key is ManagedKey) {
                try {
                    withContext(NonCancellable) { managedKeyProvider.deleteManagedKey(key.storedKey) }
                } catch (cleanupFailure: Throwable) {
                    cause.addSuppressed(cleanupFailure)
                }
            }
            throw cause
        }
        return key
    }

    private fun supportsSoftware(requirements: WalletKeyRequirements): Boolean = runCatching {
        softwareRuntime.resolveSoftwareProvider(
            CryptoRequirement(
                operation = CryptoOperation.GENERATE_KEY,
                spec = requirements.spec,
                usages = requirements.usages,
                keyEncoding = KeyEncodingFormat.JWK,
            )
        )
    }.isSuccess

    /** Persists a versioned descriptor without exporting it through the legacy key API. */
    override suspend fun addCrypto2Key(key: StoredKeyMaterial): String {
        val stored = (key as? StorableKey)?.storedKey
            ?: throw IllegalArgumentException("Mobile key persistence requires a storable key")
        require(key.id == stored.id && key.spec == stored.spec && key.usages == stored.usages) {
            "Key properties do not match the stored descriptor"
        }
        requireNotNull(restoreStoredKey(stored)) { "Stored key is unavailable: ${stored.id.value}" }

        queries.insert(
            key_id = stored.id.value,
            created_at = Clock.System.now().toEpochMilliseconds(),
            stored_key = StoredKeyCodec.encodeToString(stored),
        )
        return stored.id.value
    }

    /** Removes a persisted key and any managed native key material it owns. */
    override suspend fun removeKey(keyId: String): Boolean {
        val ref = queries.selectByKeyId(keyId).executeAsOneOrNull() ?: return false
        when (val stored = decodeStoredKey(ref.key_id, ref.stored_key)) {
            is StoredKey.Managed -> managedKeyProvider.deleteManagedKey(stored)
            is StoredKey.Software -> Unit
        }
        queries.deleteByKeyId(keyId)
        return true
    }

    private fun decodeStoredKey(keyId: String, serialized: String): StoredKey = try {
        StoredKeyCodec.decodeFromString(serialized).also { stored ->
            require(stored.id == KeyId(keyId)) { "Stored key ID does not match mobile key reference" }
            require(stored.usages.isNotEmpty()) { "Stored key usages cannot be empty" }
        }
    } catch (cause: KeyUseAuthorizationException) {
        throw cause
    } catch (cause: Throwable) {
        throw KeyUseAuthorizationException(
            KeyUseAuthorizationFailure.InvalidStoredKeyMetadata,
            "Stored mobile key descriptor is invalid",
            cause,
        )
    }

    private suspend fun restoreStoredKey(stored: StoredKey): StoredKeyMaterial? = when (stored) {
        is StoredKey.Managed -> {
            val restoration = managedKeyProvider.restoreManagedKey(stored)
            when (restoration) {
                is PlatformManagedKeyRestoration.Missing -> {
                    if (restoration.authorizationPolicy !is KeyUseAuthorizationPolicy.None) {
                        throw KeyUseAuthorizationException(
                            KeyUseAuthorizationFailure.ProtectedKeyUnavailable,
                            "Protected mobile key '${stored.id.value}' is unavailable",
                        )
                    }
                    null
                }

                is PlatformManagedKeyRestoration.Restored -> restoration.key
            }
        }
        is StoredKey.Software -> try {
            softwareRuntime.restore(stored)
        } catch (cause: Throwable) {
            throw KeyUseAuthorizationException(
                KeyUseAuthorizationFailure.InvalidStoredKeyMetadata,
                "Stored software key descriptor is invalid",
                cause,
            )
        }
    }.also { key ->
        if (key != null) {
            try {
                require(key.id == stored.id) { "Restored key ID does not match its stored descriptor" }
                require(key.spec == stored.spec) { "Restored key specification does not match its stored descriptor" }
                require(key.usages == stored.usages) { "Restored key usages do not match its stored descriptor" }
            } catch (cause: Throwable) {
                throw KeyUseAuthorizationException(
                    KeyUseAuthorizationFailure.InvalidStoredKeyMetadata,
                    "Restored key does not match its stored descriptor",
                    cause,
                )
            }
        }
    }

}
