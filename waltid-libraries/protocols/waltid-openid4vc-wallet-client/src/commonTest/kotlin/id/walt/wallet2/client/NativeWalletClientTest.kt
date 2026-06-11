package id.walt.wallet2.client

import kotlin.test.Test
import kotlin.test.assertEquals

class NativeWalletClientTest {

    @Test
    fun mobileWalletConfigDefaultsToP256() {
        val config = MobileWalletConfig()
        assertEquals(id.walt.crypto.keys.KeyType.secp256r1, config.defaultKeyType)
    }
}
