package id.walt.wallet2.mobile

import id.walt.wallet2.data.WalletKeyStore
import id.walt.crypto2.keys.KeyUseAuthorizationPolicy
import id.walt.wallet2.persistence.keys.MobileWalletKeyStore
import id.walt.wallet2.stores.inmemory.InMemoryKeyStore

internal class InMemoryMobileWalletKeyStore(
    private val delegate: InMemoryKeyStore = InMemoryKeyStore(),
) : MobileWalletKeyStore, WalletKeyStore by delegate {
    override suspend fun keyUseAuthorizationPolicy(keyId: String): KeyUseAuthorizationPolicy? =
        delegate.getKeyMaterial(keyId)?.let { KeyUseAuthorizationPolicy.None }
}
