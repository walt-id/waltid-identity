package id.walt.walletdemo

import id.walt.crypto.keys.KeyType
import id.walt.walletdemo.app.features.walletsdk.InMemoryWalletSdkAdapter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletSdkAdapterTest {

    @Test
    fun bootstrap_creates_key_and_did() = runTest {
        val adapter = InMemoryWalletSdkAdapter()
        val result = adapter.bootstrapWallet(
            keyType = KeyType.Ed25519,
            didMethod = "key",
        )

        assertTrue(result.keyId.isNotBlank())
        assertTrue(result.did.startsWith("did:key:"))
    }
}
