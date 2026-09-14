package id.walt.wallet2.persistence.keys

import id.walt.wallet2.data.WalletKeyStore

/** Mobile key store that can report the immutable authorization policy of a persisted key. */
public interface MobileWalletKeyStore : WalletKeyStore {
    /** Returns the persisted policy for [keyId], or `null` when the key does not exist. */
    public suspend fun keyUseAuthorizationPolicy(keyId: String): KeyUseAuthorizationPolicy?
}
