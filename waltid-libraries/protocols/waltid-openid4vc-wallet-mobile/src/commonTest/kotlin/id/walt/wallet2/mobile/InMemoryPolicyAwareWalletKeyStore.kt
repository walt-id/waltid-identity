package id.walt.wallet2.mobile

import id.walt.crypto2.keys.KeyUseAuthorizationPolicy
import id.walt.wallet2.data.WalletKeyStore
import id.walt.wallet2.persistence.keys.PolicyAwareWalletKeyStore
import id.walt.wallet2.stores.inmemory.InMemoryKeyStore

internal class InMemoryPolicyAwareWalletKeyStore(
    private val delegate: InMemoryKeyStore = InMemoryKeyStore(),
) : PolicyAwareWalletKeyStore, WalletKeyStore by delegate {
    override suspend fun keyUseAuthorizationPolicy(keyId: String): KeyUseAuthorizationPolicy? =
        delegate.getKeyMaterial(keyId)?.let { KeyUseAuthorizationPolicy.None }
}
