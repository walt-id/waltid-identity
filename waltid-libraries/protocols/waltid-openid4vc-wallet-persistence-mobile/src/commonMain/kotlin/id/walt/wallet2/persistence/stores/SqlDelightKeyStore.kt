package id.walt.wallet2.persistence.stores

import id.walt.crypto.keys.Key
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.ManagedKey
import id.walt.crypto2.keys.Signer
import id.walt.crypto2.keys.StorableKey
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.keys.Key as StoredKeyMaterial
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.crypto2.serialization.StoredKeyCodec
import id.walt.crypto2.signum.SignumInteractionContextUnavailableException
import id.walt.crypto2.signum.SignumKeyInvalidatedException
import id.walt.crypto2.signum.SignumKeyNotFoundException
import id.walt.crypto2.signum.SignumKeyPolicyMismatchException
import id.walt.crypto2.signum.SignumStoredKeyMetadataException
import id.walt.crypto2.signum.SignumUserCancelledException
import id.walt.wallet2.data.WalletKeyInfo
import id.walt.wallet2.data.WalletKeyStore
import id.walt.wallet2.data.WalletKeyStoreEntry
import id.walt.wallet2.persistence.db.WalletPersistenceQueries
import id.walt.wallet2.persistence.keys.PlatformManagedKeyProvider
import id.walt.wallet2.persistence.keys.KeyUseAuthorizationException
import id.walt.wallet2.persistence.keys.KeyUseAuthorizationFailure
import id.walt.wallet2.persistence.keys.KeyUseAuthorizationPolicy
import id.walt.wallet2.persistence.keys.PlatformKeyCreationRequest
import id.walt.wallet2.persistence.keys.PlatformKeyRequirements
import id.walt.wallet2.persistence.keys.PlatformKeyRequestSupport
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
) : WalletKeyStore {
    private val softwareRuntime = CryptoRuntime(defaultSoftwareKeyProviders())

    /** Legacy key material is not supported by the mobile store. */
    override suspend fun getKey(keyId: String): Key? = null

    /** Restores a persisted storable key with the requested usages. */
    override suspend fun getCrypto2Key(keyId: String, usages: Set<KeyUsage>): StoredKeyMaterial? =
        queries.selectByKeyId(keyId).executeAsOneOrNull()?.let { ref ->
            restoreStoredKey(decodeStoredKey(ref.key_id, ref.stored_key)).also { key ->
                key?.let { restored ->
                    require(usages.all(restored.usages::contains)) { "Mobile key does not permit requested usages" }
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

    /** Legacy keys must be converted to a storable key before they are persisted. */
    override suspend fun addKey(key: Key): String =
        throw UnsupportedOperationException("Mobile key storage supports storable keys only")

    /** Checks whether an exact key request can be enforced without fallback. */
    public suspend fun preflight(requirements: PlatformKeyRequirements): PlatformKeyRequestSupport =
        if (requirements.authorizationPolicy == KeyUseAuthorizationPolicy.None) {
            PlatformKeyRequestSupport.Supported
        } else {
            managedKeyProvider.preflight(requirements)
        }

    /** Generates and persists either a software or managed Crypto2 key. */
    public suspend fun generateKey(request: PlatformKeyCreationRequest): StoredKeyMaterial {
        require(queries.selectByKeyId(request.id.value).executeAsOneOrNull() == null) {
            "Mobile key already exists: ${request.id.value}"
        }
        val support = managedKeyProvider.preflight(request.requirements)
        val key = when (support) {
            PlatformKeyRequestSupport.Supported ->
                managedKeyProvider.generateManagedKey(request)
                    .withAuthorizationFailureMapping(request.requirements.authorizationPolicy)

            is PlatformKeyRequestSupport.Unsupported -> {
                if (request.requirements.authorizationPolicy != KeyUseAuthorizationPolicy.None) {
                    throw KeyUseAuthorizationException(
                        failure = support.reason.toAuthorizationFailure(),
                        message = "The platform cannot enforce ${request.requirements.authorizationPolicy} for ${request.requirements.spec}",
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
            is StoredKey.Managed -> try {
                managedKeyProvider.deleteManagedKey(stored)
            } catch (cause: SignumStoredKeyMetadataException) {
                throw KeyUseAuthorizationException(
                    KeyUseAuthorizationFailure.InvalidStoredKeyMetadata,
                    "Stored managed key metadata is invalid",
                    cause,
                )
            }
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
            val restoration = try {
                managedKeyProvider.restoreManagedKey(stored)
            } catch (cause: SignumKeyInvalidatedException) {
                throw KeyUseAuthorizationException(
                    KeyUseAuthorizationFailure.ProtectedKeyUnavailable,
                    "Protected mobile key '${stored.id.value}' is unavailable",
                    cause,
                )
            } catch (cause: SignumKeyPolicyMismatchException) {
                throw KeyUseAuthorizationException(
                    KeyUseAuthorizationFailure.ProtectedKeyUnavailable,
                    "Protected mobile key '${stored.id.value}' is unavailable",
                    cause,
                )
            } catch (cause: SignumStoredKeyMetadataException) {
                throw KeyUseAuthorizationException(
                    KeyUseAuthorizationFailure.InvalidStoredKeyMetadata,
                    "Stored managed key metadata is invalid",
                    cause,
                )
            }
            when (restoration) {
                is PlatformManagedKeyRestoration.Missing -> {
                    if (restoration.authorizationPolicy != KeyUseAuthorizationPolicy.None) {
                        throw KeyUseAuthorizationException(
                            KeyUseAuthorizationFailure.ProtectedKeyUnavailable,
                            "Protected mobile key '${stored.id.value}' is unavailable",
                        )
                    }
                    null
                }

                is PlatformManagedKeyRestoration.Restored ->
                    restoration.key.withAuthorizationFailureMapping(restoration.authorizationPolicy)
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

    private fun ManagedKey.withAuthorizationFailureMapping(
        authorizationPolicy: KeyUseAuthorizationPolicy,
    ): ManagedKey = if (authorizationPolicy == KeyUseAuthorizationPolicy.None) {
        this
    } else {
        object : ManagedKey {
            override val storedKey: StoredKey.Managed = this@withAuthorizationFailureMapping.storedKey
            override val capabilities = this@withAuthorizationFailureMapping.capabilities.copy(
                signer = this@withAuthorizationFailureMapping.capabilities.signer?.let { signer ->
                    Signer { data, algorithm ->
                        try {
                            signer.sign(data, algorithm)
                        } catch (cause: SignumUserCancelledException) {
                            throw KeyUseAuthorizationException(
                                KeyUseAuthorizationFailure.AuthorizationNotCompleted,
                                "Protected mobile key authorization was not completed",
                                cause,
                            )
                        } catch (cause: SignumInteractionContextUnavailableException) {
                            throw KeyUseAuthorizationException(
                                KeyUseAuthorizationFailure.InteractionContextUnavailable,
                                "Protected mobile key interaction context is unavailable",
                                cause,
                            )
                        } catch (cause: SignumKeyInvalidatedException) {
                            throw KeyUseAuthorizationException(
                                KeyUseAuthorizationFailure.ProtectedKeyUnavailable,
                                "Protected mobile key '${storedKey.id.value}' is unavailable",
                                cause,
                            )
                        } catch (cause: SignumKeyNotFoundException) {
                            throw KeyUseAuthorizationException(
                                KeyUseAuthorizationFailure.ProtectedKeyUnavailable,
                                "Protected mobile key '${storedKey.id.value}' is unavailable",
                                cause,
                            )
                        }
                    }
                },
            )
        }
    }
}
