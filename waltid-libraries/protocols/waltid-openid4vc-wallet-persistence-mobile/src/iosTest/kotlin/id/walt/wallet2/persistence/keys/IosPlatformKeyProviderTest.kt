package id.walt.wallet2.persistence.keys

import id.walt.crypto.keys.KeyType
import kotlin.test.Test
import kotlin.test.assertEquals

class IosPlatformKeyProviderTest {

    @Test
    fun supportsPlatformKeyTypes() {
        assertEquals(
            setOf(KeyType.secp256r1, KeyType.secp384r1, KeyType.secp521r1, KeyType.RSA),
            IosPlatformKeyProvider().supportedPlatformKeyTypes,
        )
    }
}
