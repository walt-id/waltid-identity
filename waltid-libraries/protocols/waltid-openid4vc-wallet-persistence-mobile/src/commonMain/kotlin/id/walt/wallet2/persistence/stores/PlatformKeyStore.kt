package id.walt.wallet2.persistence.stores

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyHardwareBacking
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.KeyUseAuthorizationAware
import id.walt.crypto.keys.KeyUseAuthorizationException
import id.walt.crypto.keys.KeyUseAuthorizationFailure
import id.walt.crypto.keys.KeyUseAuthorizationPolicy
import id.walt.wallet2.persistence.db.WalletPersistenceQueries
import id.walt.wallet2.persistence.keys.PlatformKeyProvider
import id.walt.wallet2.data.WalletKeyInfo
import id.walt.wallet2.data.WalletKeyStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Clock

/**
 * Wallet key store that persists key metadata in SQLDelight.
 *
 * Platform-backed keys keep their material in the platform key store. Software keys store serialized key
 * material in the SQLDelight table so they can be reloaded on platforms that require a fallback.
 *
 * @param keyProvider Platform key provider used to load, export, and delete keys.
 * @param queries SQLDelight queries for wallet persistence tables.
 */
public class PlatformKeyStore(
    private val keyProvider: PlatformKeyProvider,
    private val queries: WalletPersistenceQueries,
) : WalletKeyStore {

    override val supportsKeyUseAuthorizationMetadata: Boolean = true

    /**
     * Loads a wallet key by its wallet-local key identifier.
     */
    override suspend fun getKey(keyId: String): Key? {
        val ref = queries.selectByKeyId(keyId).executeAsOneOrNull() ?: return null
        val keyType = KeyType.valueOf(ref.key_type)
        val authorizationPolicy = KeyUseAuthorizationPolicy.valueOf(ref.effective_authorization_policy)
        if (
            authorizationPolicy != KeyUseAuthorizationPolicy.None &&
            ref.is_platform_backed != 1L
        ) {
            throw invalidProtectedKey(keyId, "is not marked as platform-backed")
        }

        val key = if (ref.is_platform_backed == 1L) {
            keyProvider.loadKey(keyId, keyType, authorizationPolicy)
        } else {
            val material = ref.key_material?.encodeToByteArray()
                ?: error("Software key '$keyId' has no stored key material")
            keyProvider.loadSoftwareKey(keyId, keyType, material)
        }

        if (authorizationPolicy == KeyUseAuthorizationPolicy.None) return key
        if (key == null) {
            throw KeyUseAuthorizationException(
                failure = KeyUseAuthorizationFailure.ProtectedKeyMissing,
                message = "The protected key '$keyId' is missing from the platform key store",
            )
        }
        val authorizationAware = key as? KeyUseAuthorizationAware
        if (
            key.keyType != keyType ||
            authorizationAware == null ||
            authorizationAware.keyUseAuthorizationPolicy != authorizationPolicy ||
            !authorizationAware.isPlatformBacked
        ) {
            throw invalidProtectedKey(keyId, "does not enforce its persisted authorization policy")
        }
        return key
    }

    /**
     * Streams all persisted key references.
     */
    override suspend fun listKeys(): Flow<WalletKeyInfo> = flow {
        queries.selectAll().executeAsList().forEach { ref ->
            emit(
                WalletKeyInfo(
                    keyId = ref.key_id,
                    keyType = ref.key_type,
                    requestedKeyUseAuthorizationPolicy =
                        KeyUseAuthorizationPolicy.valueOf(ref.requested_authorization_policy),
                    effectiveKeyUseAuthorizationPolicy =
                        KeyUseAuthorizationPolicy.valueOf(ref.effective_authorization_policy),
                    isPlatformBacked = ref.is_platform_backed == 1L,
                    effectiveHardwareBacking = ref.effective_hardware_backing?.let(KeyHardwareBacking::valueOf),
                )
            )
        }
    }

    /**
     * Persists a key reference for [key].
     *
     * Platform-backed key material remains in the platform key store. Software key material is serialized
     * into the SQLDelight table.
     */
    override suspend fun addKey(key: Key): String {
        return persistKey(key, keyInfo = null)
    }

    override suspend fun addKey(key: Key, keyInfo: WalletKeyInfo): String {
        return persistKey(key, keyInfo)
    }

    private suspend fun persistKey(key: Key, keyInfo: WalletKeyInfo?): String {
        val keyId = key.getKeyId()
        val authorizationAware = key as? KeyUseAuthorizationAware
        val requestedAuthorizationPolicy = keyInfo?.requestedKeyUseAuthorizationPolicy
            ?: authorizationAware?.keyUseAuthorizationPolicy
            ?: KeyUseAuthorizationPolicy.None
        val effectiveAuthorizationPolicy = authorizationAware?.keyUseAuthorizationPolicy
            ?: KeyUseAuthorizationPolicy.None
        val isPlatformBacked = authorizationAware?.isPlatformBacked
            ?: keyInfo?.isPlatformBacked
            ?: keyProvider.isPlatformBacked(key.keyType)
        val hasProtectedPolicy =
            requestedAuthorizationPolicy != KeyUseAuthorizationPolicy.None ||
                effectiveAuthorizationPolicy != KeyUseAuthorizationPolicy.None
        if (
            hasProtectedPolicy &&
            (
                authorizationAware == null ||
                    !isPlatformBacked ||
                    keyInfo?.effectiveKeyUseAuthorizationPolicy?.let { it != effectiveAuthorizationPolicy } == true
            )
        ) {
            throw KeyUseAuthorizationException(
                failure = KeyUseAuthorizationFailure.UnsupportedCombination,
                message = "Protected key metadata must match a platform-backed authorization-aware key",
            )
        }
        val effectiveHardwareBacking = authorizationAware?.effectiveHardwareBacking()
            ?: keyInfo?.effectiveHardwareBacking
        val material = if (!isPlatformBacked) keyProvider.exportSoftwareKeyMaterial(key) else null

        queries.insert(
            key_id = keyId,
            key_type = key.keyType.name,
            created_at = Clock.System.now().toEpochMilliseconds(),
            is_platform_backed = if (isPlatformBacked) 1L else 0L,
            key_material = material?.decodeToString(),
            requested_authorization_policy = requestedAuthorizationPolicy.name,
            effective_authorization_policy = effectiveAuthorizationPolicy.name,
            effective_hardware_backing = effectiveHardwareBacking?.name,
        )
        return keyId
    }

    /**
     * Removes the platform-backed key when present and deletes its SQLDelight key reference.
     */
    override suspend fun removeKey(keyId: String): Boolean {
        val ref = queries.selectByKeyId(keyId).executeAsOneOrNull() ?: return false
        val keyType = KeyType.valueOf(ref.key_type)
        keyProvider.deleteKey(keyId, keyType)
        queries.deleteByKeyId(keyId)
        return true
    }

    private fun invalidProtectedKey(keyId: String, reason: String): KeyUseAuthorizationException =
        KeyUseAuthorizationException(
            failure = KeyUseAuthorizationFailure.ProtectedKeyInvalidated,
            message = "The protected key '$keyId' $reason",
        )
}
