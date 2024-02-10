package id.walt.crypto.keys

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class LocalKeyTest {

    @Test
    fun generate_key_pair_using_RSA_algorithm() = runTest {
        val keyPair = AndroidLocalKeyGenerator.generate(KeyType.RSA)
        assertTrue { keyPair.hasPrivateKey }
        assertTrue { keyPair.toString().contains("RSA") }
    }

    @Test
    fun generate_key_pair_using_ECC_algorithm() = runTest {
        val keyPair = AndroidLocalKeyGenerator.generate(KeyType.secp256r1)
        assertTrue { keyPair.hasPrivateKey }
        assertTrue { keyPair.toString().contains("secp256r1") }
    }

    @Test
    fun public_key_retrieved_from_keypair_has_the_same_algorithm_as_keypair() = runTest {
        val rsaKeyPair = AndroidLocalKeyGenerator.generate(KeyType.RSA)
        val rsaPublicKey = rsaKeyPair.getPublicKey()

        assertTrue { rsaPublicKey.toString().contains("RSA") }
        assertFalse { rsaPublicKey.hasPrivateKey}

        val eccKeyPair = AndroidLocalKeyGenerator.generate(KeyType.secp256r1)
        val eccPublicKey = eccKeyPair.getPublicKey()

        assertTrue { eccPublicKey.toString().contains("secp256r1") }
        assertFalse { eccPublicKey.hasPrivateKey}
    }
}