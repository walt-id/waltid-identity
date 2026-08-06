import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

class Ed25519HpkeTest {
    @Test
    fun rejectsEd25519AsX25519HpkeKey() = runTest {
        val key = JWKKey.generate(KeyType.Ed25519)

        assertFailsWith<IllegalArgumentException> {
            key.encryptHpke("plaintext".encodeToByteArray(), byteArrayOf(), byteArrayOf())
        }
    }
}
