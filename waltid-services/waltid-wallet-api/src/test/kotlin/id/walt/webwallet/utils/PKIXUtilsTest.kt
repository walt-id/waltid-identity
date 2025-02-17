package id.walt.webwallet.utils

import kotlinx.coroutines.test.runTest
import java.security.KeyPairGenerator
import kotlin.test.Test
import kotlin.test.assertTrue

class PKIXUtilsTest {

    //we don't care about the bit size of the key, it's a test case (as long as it's bigger than 512)
    private val keyPairGenerator = KeyPairGenerator
        .getInstance("RSA").apply {
            initialize(1024)
        }

    @Test
    fun testPEMEncodedPrivateKeyParsing() = runTest {
        val keyPair = keyPairGenerator.generateKeyPair()
        val pemEncodedPrivateKey = PKIXUtils.pemEncodeJavaPrivateKey(keyPair.private)
        val decodedPrivateKey = PKIXUtils.pemDecodeJavaPrivateKey(pemEncodedPrivateKey)
        assertTrue { keyPair.private.encoded.contentEquals(decodedPrivateKey.encoded) }
    }
}
