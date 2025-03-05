package id.walt.crypto.keys

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
@RunWith(AndroidJUnit4::class)
class AndroidKeyTest {

    @Test
    fun generate_key_pair_using_RSA_algorithm() = runTest {
        val keyPair = AndroidKeyGenerator.generate(KeyType.RSA)
        assertTrue { keyPair.hasPrivateKey }
        assertTrue { keyPair.toString().contains("RSA") }
    }

    @Test
    fun generate_key_pair_using_ECC_algorithm() = runTest {
        val keyPair = AndroidKeyGenerator.generate(KeyType.secp256r1)
        assertTrue { keyPair.hasPrivateKey }
        assertTrue { keyPair.toString().contains("secp256r1") }
    }

    @Test
    fun public_key_retrieved_from_keypair_has_the_same_algorithm_as_keypair() = runTest {
        val rsaKeyPair = AndroidKeyGenerator.generate(KeyType.RSA)
        val rsaPublicKey = rsaKeyPair.getPublicKey()

        assertTrue { rsaPublicKey.toString().contains("RSA") }
        assertFalse { rsaPublicKey.hasPrivateKey }

        val eccKeyPair = AndroidKeyGenerator.generate(KeyType.secp256r1)
        val eccPublicKey = eccKeyPair.getPublicKey()

        assertTrue { eccPublicKey.toString().contains("secp256r1") }
        assertFalse { eccPublicKey.hasPrivateKey }
    }

    @Test
    fun return_same_instance_when_calling_retrieving_public_key_when_instance_does_not_have_a_keypair() = runTest {
        val rsaKeyPair = AndroidKeyGenerator.generate(KeyType.RSA)
        val rsaPublicKey = rsaKeyPair.getPublicKey()

        assertTrue { rsaPublicKey.toString().contains("RSA") }
        assertFalse { rsaPublicKey.hasPrivateKey }

        val identicalRSAPublicKey = rsaPublicKey.getPublicKey()

        assertTrue { identicalRSAPublicKey.toString().contains("RSA") }
        assertTrue { identicalRSAPublicKey == rsaPublicKey }
        assertFalse { identicalRSAPublicKey.hasPrivateKey }

        val eccKeyPair = AndroidKeyGenerator.generate(KeyType.secp256r1)
        val eccPublicKey = eccKeyPair.getPublicKey()

        assertTrue { eccPublicKey.toString().contains("secp256r1") }
        assertFalse { eccPublicKey.hasPrivateKey }

        val identicalEccPublicKey = eccPublicKey.getPublicKey()

        assertTrue { identicalEccPublicKey.toString().contains("secp256r1") }
        assertTrue { identicalEccPublicKey == eccPublicKey }
        assertFalse { identicalEccPublicKey.hasPrivateKey }
    }

    //@Test
    fun use_ECC_key() = runTest {
        val keyPair = AndroidKeyGenerator.generate(KeyType.secp256r1)
        val plaintext = "1234abcd".encodeToByteArray()
        val signed = keyPair.signRaw(plaintext)
        val result = keyPair.verifyRaw(signed, plaintext)

        assertTrue { result.isSuccess }
    }

    @Test
    fun load_not_existing_key() = runTest {
        val randomKid = Uuid.random().toString()
        val key = AndroidKey.load(KeyType.secp256r1, randomKid)
        assertNull(key, "Should not load key that does not exist.")
    }

    @Test
    fun create_then_load_key() = runTest {
        val randomKid = Uuid.random().toString()
        val key = AndroidKey.generate(KeyType.secp256r1, AndroidKeyParameters(randomKid))
        assertNotNull(key, "Should return key just created.")
        val loaded = AndroidKey.load(KeyType.secp256r1, randomKid)
        assertNotNull(loaded,"Should return loaded key.")
        assertEquals(key.getKeyId(), loaded.getKeyId(), "kid's are not equal")
    }
}
