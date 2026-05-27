package id.walt.wallet2.client

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NativeWalletClientTest {

    @Test
    fun bootstrapCreatesWalletKeyAndDid() = runTest {
        val client = NativeWalletClient(walletId = "test-wallet")

        val result = client.bootstrap()

        assertTrue(result.keyId.isNotBlank())
        assertTrue(result.did.startsWith("did:key:"))
        assertNotNull(client.credentials())
    }
}
