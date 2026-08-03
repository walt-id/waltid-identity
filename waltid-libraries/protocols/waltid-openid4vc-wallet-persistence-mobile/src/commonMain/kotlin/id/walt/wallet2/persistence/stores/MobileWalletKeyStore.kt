package id.walt.wallet2.persistence.stores

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.KeyUseAuthorizationPolicy
import kotlinx.coroutines.flow.Flow

/**
 * Immutable, wallet-local metadata for one mobile signing key.
 *
 * @property keyId Stable wallet-local key identifier.
 * @property keyType Stored signing-key type.
 * @property keyUseAuthorizationPolicy Immutable per-key authorization policy.
 * @property isPlatformBacked Whether private material is held by a platform key store.
 */
public data class MobileWalletKeyRecord(
    public val keyId: String,
    public val keyType: KeyType,
    public val keyUseAuthorizationPolicy: KeyUseAuthorizationPolicy = KeyUseAuthorizationPolicy.None,
    public val isPlatformBacked: Boolean,
)

/** Mobile-specific key persistence contract. */
public interface MobileWalletKeyStore {
    /** Loads a key by identifier, returning `null` only when an unprotected key is absent. */
    public suspend fun getKey(keyId: String): Key?

    /** Atomically persists [key] together with its complete immutable [record]. */
    public suspend fun addKey(key: Key, record: MobileWalletKeyRecord): String

    /** Lists complete immutable metadata for all stored mobile signing keys. */
    public suspend fun listKeyRecords(): Flow<MobileWalletKeyRecord>

    /** Removes the key record and its underlying platform key as one lifecycle operation. */
    public suspend fun removeKey(keyId: String): Boolean
}
