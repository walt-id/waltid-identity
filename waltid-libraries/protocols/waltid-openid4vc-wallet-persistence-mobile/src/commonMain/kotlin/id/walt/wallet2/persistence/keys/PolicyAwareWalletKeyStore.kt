package id.walt.wallet2.persistence.keys

import id.walt.crypto2.keys.KeyUseAuthorizationPolicy
import id.walt.wallet2.data.WalletKeyStore

/** Wallet key store that reports the immutable authorization policy of a persisted key. */
public interface PolicyAwareWalletKeyStore : WalletKeyStore {
    /** Returns the persisted policy for [keyId], or `null` when the key does not exist. */
    public suspend fun keyUseAuthorizationPolicy(keyId: String): KeyUseAuthorizationPolicy?
}
