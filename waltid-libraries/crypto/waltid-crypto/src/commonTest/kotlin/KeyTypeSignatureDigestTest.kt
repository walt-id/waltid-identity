import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.digestForSignature
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class KeyTypeSignatureDigestTest {
    private val message = "abc".encodeToByteArray()

    @Test
    fun selectsDigestBySignatureAlgorithmFamily() {
        val sha256 = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad".hexToByteArray()
        val sha384 = (
            "cb00753f45a35e8bb5a03d699ac65007272c32ab0eded163" +
                "1a8b605a43ff5bed8086072ba1e7cc2358baeca134c825a7"
            ).hexToByteArray()
        val sha512 = (
            "ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20a9eeee64b55d39a" +
                "2192992a274fc1a836ba3c23a3feebbd454d4423643ce80e2a9ac94fa54ca49f"
            ).hexToByteArray()

        listOf(KeyType.secp256k1, KeyType.secp256r1, KeyType.RSA).forEach {
            assertContentEquals(sha256, it.digestForSignature(message))
        }
        listOf(KeyType.secp384r1, KeyType.RSA3072).forEach {
            assertContentEquals(sha384, it.digestForSignature(message))
        }
        listOf(KeyType.secp521r1, KeyType.RSA4096).forEach {
            assertContentEquals(sha512, it.digestForSignature(message))
        }
        assertFailsWith<IllegalArgumentException> { KeyType.Ed25519.digestForSignature(message) }
    }
}
