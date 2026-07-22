package id.walt.crypto2.algorithms

import id.walt.crypto2.keys.DigestSigner
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DigestValueTest {
    @Test
    fun `known digest sizes are enforced`() {
        DigestValue(DigestAlgorithm.SHA_256, ByteArray(32))

        assertFailsWith<IllegalArgumentException> {
            DigestValue(DigestAlgorithm.SHA_256, ByteArray(31))
        }
        assertFailsWith<IllegalArgumentException> {
            DigestValue(DigestAlgorithm.SHA_512, byteArrayOf())
        }
    }

    @Test
    fun `custom digest remains extensible`() {
        DigestValue(DigestAlgorithm("provider-digest"), ByteArray(17))
    }

    @Test
    fun `digest signing is a distinct capability`() {
        val digestSigner = DigestSigner { _, _ -> byteArrayOf(1) }
        val key = object : Key, DigestSigner by digestSigner {
            override val id = KeyId("digest-key")
            override val spec = KeySpec.Rsa(2048)
            override val usages = setOf(KeyUsage.SIGN)
        }

        assertNotNull(key.capabilities.digestSigner)
        assertNull(key.capabilities.signer)
    }
}
