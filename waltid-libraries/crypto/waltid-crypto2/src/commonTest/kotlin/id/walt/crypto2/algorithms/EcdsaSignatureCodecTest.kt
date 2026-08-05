package id.walt.crypto2.algorithms

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class EcdsaSignatureCodecTest {
    @Test
    fun `P1363 and DER round trip for supported component sizes`() {
        listOf(32, 48, 66).forEach { size ->
            val signature = ByteArray(size * 2) { index ->
                when (index) {
                    0 -> 0x80.toByte()
                    size -> 0
                    else -> (index + 1).toByte()
                }
            }
            val der = EcdsaSignatureCodec.p1363ToDer(signature, size)

            assertContentEquals(signature, EcdsaSignatureCodec.derToP1363(der, size))
        }
    }

    @Test
    fun `malformed or wrong-sized signatures are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            EcdsaSignatureCodec.p1363ToDer(ByteArray(63), 32)
        }
        assertFailsWith<IllegalArgumentException> {
            EcdsaSignatureCodec.derToP1363(byteArrayOf(0x30, 0x00), 32)
        }
        assertFailsWith<IllegalArgumentException> {
            EcdsaSignatureCodec.derToP1363(byteArrayOf(0x30, 0x03, 0x02, 0x01, 0xff.toByte()), 32)
        }
    }
}
