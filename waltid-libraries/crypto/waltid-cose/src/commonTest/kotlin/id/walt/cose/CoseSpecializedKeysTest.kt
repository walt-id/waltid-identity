package id.walt.cose

import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.serialization.BinaryData
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails

class CoseSpecializedKeysTest {
    private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

    @Test
    fun `RSA private JWK and COSE_Key round trip`() {
        fun value(byte: Int) = base64Url.encode(ByteArray(16) { byte.toByte() })
        val jwk = EncodedKey.Jwk(
            BinaryData(
                """{"kty":"RSA","n":"${value(1)}","e":"AQAB","d":"${value(2)}","p":"${value(3)}","q":"${value(4)}","dp":"${value(5)}","dq":"${value(6)}","qi":"${value(7)}"}"""
                    .encodeToByteArray(),
            ),
            privateMaterial = true,
        )

        val cose = jwk.toCoseRsaKey(algorithm = Cose.Algorithm.PS256)
        val restored = CoseRsaKey.deserialize(cose.serialize())

        assertEquals(Cose.KeyTypes.RSA, restored.kty)
        assertContentEquals(cose.n, restored.n)
        assertContentEquals(cose.d, restored.d)
        assertEquals(true, restored.toEncodedJwk().privateMaterial)
    }

    @Test
    fun `symmetric JWK and COSE_Key round trip`() {
        val secret = ByteArray(32) { it.toByte() }
        val jwk = EncodedKey.Jwk(
            BinaryData("""{"kty":"oct","k":"${base64Url.encode(secret)}"}""".encodeToByteArray()),
            privateMaterial = true,
        )

        val cose = jwk.toCoseSymmetricKey(algorithm = Cose.Algorithm.HMAC_256)
        val restored = CoseSymmetricKey.deserialize(cose.serialize())

        assertContentEquals(secret, restored.k)
        assertEquals(true, restored.toEncodedJwk().privateMaterial)
    }

    @Test
    fun `partial RSA private parameters are rejected`() {
        assertFails {
            CoseRsaKey(
                kty = Cose.KeyTypes.RSA,
                n = byteArrayOf(1),
                e = byteArrayOf(1, 0, 1),
                d = byteArrayOf(2),
            )
        }
    }
}
