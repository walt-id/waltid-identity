package id.walt.crypto2.hash

import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals

class HashAlgorithmJvmTest {

    @Test
    fun `jca names resolve successfully`() {
        HashAlgorithm.entries.forEach { algorithm ->
            val jcaName = algorithm.toJcaName()
            val messageDigest = MessageDigest.getInstance(jcaName)
            assertEquals(jcaName, messageDigest.algorithm, "Unexpected JCA algorithm for ${algorithm.name}")
        }
    }
}
