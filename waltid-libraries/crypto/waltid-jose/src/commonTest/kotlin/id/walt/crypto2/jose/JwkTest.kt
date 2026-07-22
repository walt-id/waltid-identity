package id.walt.crypto2.jose

import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.serialization.BinaryData
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFails
import kotlin.io.encoding.Base64

class JwkTest {
    @Test
    fun `RFC 7638 RSA SHA-256 thumbprint vector`() = runTest {
        val modulus = "0vx7agoebGcQSuuPiLJXZptN9nndrQmbXEps2aiAFbWhM78LhWx4cbbfAAt" +
            "VT86zwu1RK7aPFFxuhDR1L6tSoc_BJECPebWKRXjBZCiFV4n3oknjhMstn6" +
            "4tZ_2W-5JsGY4Hc5n9yBXArwl93lqt7_RN5w6Cf0h4QyQ5v-65YGjQR0_FD" +
            "W2QvzqY368QQMicAtaSqzs8KJZgnYb9c7d0zgdAZHzu6qMQvRL5hajrn1n9" +
            "1CbOpbISD08qNLyrdkt-bFTWhAI4vMQFh6WeZu0fM4lFd2NcRwr3XPksINH" +
            "aQ-G_xBniIqbw0Ls1jF44-csFCur-kEgU8awapJzKnqDKgw"
        val key = EncodedKey.Jwk(
            BinaryData(
                """{"kty":"RSA","n":"$modulus","e":"AQAB","alg":"RS256","kid":"2011-04-29"}"""
                    .encodeToByteArray(),
            ),
            privateMaterial = false,
        )

        assertEquals("NzbLsXh8uDCcd-6MNwXF4W_7noWXFZAfHkxZsRGC9Xs", Jwk.sha256Thumbprint(key))
    }

    @Test
    fun `metadata is typed and private material is preserved`() {
        val key = EncodedKey.Jwk(
            BinaryData("""{"kty":"OKP","crv":"Ed25519","x":"public","d":"private"}""".encodeToByteArray()),
            privateMaterial = true,
        )
        val metadata = JwkMetadata(
            keyId = "key-id",
            use = JwkUse.SIGNATURE,
            operations = setOf(JwkOperation.SIGN, JwkOperation.VERIFY),
            algorithm = "EdDSA",
        )

        val updated = Jwk.withMetadata(key, metadata)

        assertEquals(metadata, Jwk.metadata(updated))
        assertEquals(true, updated.privateMaterial)
        assertFalse("private" in updated.data.toString())
    }

    @Test
    fun `malformed and contradictory metadata is rejected`() {
        fun key(json: String) = EncodedKey.Jwk(BinaryData(json.encodeToByteArray()), privateMaterial = false)

        assertFails { Jwk.metadata(key("""{"kty":"EC","kid":1}""")) }
        assertFails { Jwk.metadata(key("""{"kty":"EC","key_ops":"sign"}""")) }
        assertFails { Jwk.metadata(key("""{"kty":"EC","key_ops":["sign","sign"]}""")) }
        assertFails { Jwk.metadata(key("""{"kty":"EC","use":"sig","key_ops":["decrypt"]}""")) }
        assertFails { Jwk.metadata(key("""{"kty":"EC","use":"unknown"}""")) }

        val emptyOperations = key("""{"kty":"EC","key_ops":[]}""")
        assertEquals(emptySet(), Jwk.metadata(emptyOperations).operations)
        assertEquals(emptySet(), Jwk.metadata(Jwk.withMetadata(emptyOperations, JwkMetadata(operations = emptySet()))).operations)
    }

    @Test
    fun `private key material is detected by key type`() {
        fun jwk(value: String) = Json.parseToJsonElement(value).jsonObject

        assertEquals(false, Jwk.containsPrivateMaterial(jwk("""{"kty":"EC","x":"x","y":"y"}""")))
        assertEquals(true, Jwk.containsPrivateMaterial(jwk("""{"kty":"EC","x":"x","y":"y","d":"d"}""")))
        assertEquals(true, Jwk.containsPrivateMaterial(jwk("""{"kty":"OKP","crv":"Ed25519","x":"x","d":"d"}""")))
        assertEquals(true, Jwk.containsPrivateMaterial(jwk("""{"kty":"RSA","n":"n","e":"e","p":"p"}""")))
        assertEquals(true, Jwk.containsPrivateMaterial(jwk("""{"kty":"oct","k":"secret"}""")))
        assertFails { Jwk.containsPrivateMaterial(jwk("""{"kty":"custom","private":"unknown"}""")) }
    }

    @Test
    fun `thumbprint rejects malformed required members`() = runTest {
        fun key(json: String) = EncodedKey.Jwk(BinaryData(json.encodeToByteArray()), privateMaterial = false)

        assertFails { Jwk.sha256Thumbprint(key("""{"kty":"RSA","e":65537,"n":"AQ"}""")) }
        assertFails { Jwk.sha256Thumbprint(key("""{"kty":"RSA","e":"AAEAAQ","n":"AQ"}""")) }
        assertFails { Jwk.sha256Thumbprint(key("""{"kty":"RSA","e":"AQAB==","n":"AQ"}""")) }
        assertFails {
            Jwk.sha256Thumbprint(key("""{"kty":"EC","crv":"P-256","x":"AQ","y":"AQ"}"""))
        }

        val nonCanonicalX25519 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(ByteArray(32) { 0xff.toByte() })
        assertFails {
            Jwk.sha256Thumbprint(key("""{"kty":"OKP","crv":"X25519","x":"$nonCanonicalX25519"}"""))
        }
    }
}
