@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.cose

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class CoseSerializationTests {

    val key by lazy {
        suspend { JWKKey.Companion.generate(KeyType.secp256r1) }
    }

    @Test
    fun `Check serialization to JSON`() = runTest {
        val payload = "abc 123".encodeToByteArray()

        val cose = CoseSign1.createAndSign(
            protectedHeaders = CoseHeaders(algorithm = Cose.Algorithm.RS256),
            unprotectedHeaders = CoseHeaders(),
            payload = payload,
            signer = key().toCoseSigner()
        )
        println("Signed COSE: $cose")

        val encoded = Json.encodeToString(cose)
        println("Encoded to JSON: $encoded")

        val decoded = Json.decodeFromString<CoseSign1>(encoded)
        println("Decoded from JSON: $decoded")

        assertEquals(cose, decoded)
    }

    @Test
    fun `Check serialization to ByteArray`() = runTest {
        val payload = "abc 123".encodeToByteArray()

        val cose = CoseSign1.createAndSign(
            protectedHeaders = CoseHeaders(algorithm = Cose.Algorithm.RS256),
            unprotectedHeaders = CoseHeaders(),
            payload = payload,
            signer = key().toCoseSigner()
        )
        println("Signed COSE: $cose")

        val encoded = cose.toTagged()
        println("Encoded to JSON: $encoded")

        val decoded = CoseSign1.fromTagged(encoded)
        println("Decoded from JSON: $decoded")

        assertEquals(cose, decoded)
    }


    @Test
    fun `Check serialization to Hex`() = runTest {
        val payload = "abc 123".encodeToByteArray()

        val cose = CoseSign1.createAndSign(
            protectedHeaders = CoseHeaders(algorithm = Cose.Algorithm.RS256),
            unprotectedHeaders = CoseHeaders(),
            payload = payload,
            signer = key().toCoseSigner()
        )
        println("Signed COSE: $cose")

        val encoded = cose.toTagged().toHexString()
        println("Encoded to JSON: $encoded")

        val decoded = CoseSign1.fromTagged(encoded)
        println("Decoded from JSON: $decoded")

        assertEquals(cose, decoded)
    }

    @Test
    fun `Check deserialization of bytearray payload`() {
        val cose = CoseSign1.fromTagged("8445A101390100A054546869732069732074686520636F6E74656E742E43626172")
        println("Decoded COSE: $cose")
        assertEquals(cose.payload!!.decodeToString(), "This is the content.")
    }

    @Test
    fun `Check deserialization of null payload`() {
        val cose = CoseSign1.fromTagged("8445A101390100A0F643626172")
        println("Decoded COSE: $cose")
        assertEquals(cose.payload, null)
    }

    @Test
    fun `Check deserialization of unprotected header`() {
        val cose =
            CoseSign1.fromTagged("d28443a10126a10442313154546869732069732074686520636f6e74656e742e58408eb33e4ca31d1c465ab05aac34cc6b23d58fef5c083106c4d25a91aef0b0117e2af9a291aa32e14ab834dc56ed2a223444547e01f11d3b0916e5a4c345cacb36")
        println("Decoded COSE: $cose")
        assertEquals(cose.payload!!.decodeToString(), "This is the content.")
    }
}
