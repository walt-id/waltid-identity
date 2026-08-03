package id.walt.wallet2.persistence.stores

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.KeyUseAuthorizationPolicy
import id.walt.wallet2.data.WalletKeyStore
import kotlinx.coroutines.flow.Flow

/** Immutable, wallet-local metadata for one mobile signing key. */
public data class MobileWalletKeyRecord(
    public val keyId: String,
    public val keyType: KeyType,
    public val keyUseAuthorizationPolicy: KeyUseAuthorizationPolicy = KeyUseAuthorizationPolicy.None,
    public val isPlatformBacked: Boolean,
)

/** Mobile-specific key persistence contract. */
public interface MobileWalletKeyStore : WalletKeyStore {
    public suspend fun addKey(key: Key, record: MobileWalletKeyRecord): String

    public suspend fun listKeyRecords(): Flow<MobileWalletKeyRecord>
}
