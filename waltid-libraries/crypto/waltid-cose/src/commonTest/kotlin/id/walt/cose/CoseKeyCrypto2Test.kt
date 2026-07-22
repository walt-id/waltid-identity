package id.walt.cose

import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.serialization.BinaryData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class CoseKeyCrypto2Test {
    @Test
    fun `P-256 JWK and COSE_Key round trip`() {
        val jwk = EncodedKey.Jwk(
            BinaryData(
                """{"kty":"EC","crv":"P-256","x":"AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE","y":"AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI"}"""
                    .encodeToByteArray(),
            ),
            privateMaterial = false,
        )

        val cose = jwk.toCoseKey(algorithm = Cose.Algorithm.ES256, keyId = byteArrayOf(1))
        val restored = cose.toEncodedJwk().toCoseKey()

        assertEquals(Cose.KeyTypes.EC2, cose.kty)
        assertEquals(Cose.EllipticCurves.P_256, cose.crv)
        assertContentEquals(cose.x, restored.x)
        assertContentEquals(cose.y, restored.y)
        val restoredJwk = cose.toEncodedJwk()
        assertFalse(restoredJwk.privateMaterial)
        assertFalse("d" in Json.parseToJsonElement(restoredJwk.data.toByteArray().decodeToString()).jsonObject)
    }

    @Test
    fun `X25519 and Ed448 map to distinct COSE curves`() {
        fun key(curve: String, size: Int) = EncodedKey.Jwk(
            BinaryData(
                """{"kty":"OKP","crv":"$curve","x":"${Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(ByteArray(size))}"}"""
                    .encodeToByteArray(),
            ),
            privateMaterial = false,
        )

        assertEquals(Cose.EllipticCurves.X25519, key("X25519", 32).toCoseKey().crv)
        assertEquals(Cose.EllipticCurves.Ed448, key("Ed448", 57).toCoseKey().crv)
    }

    @Test
    fun `JWK key type and curve combinations are enforced`() {
        listOf(
            jwk("""{"kty":"EC","crv":"Ed25519","x":"AQ","y":"Ag"}"""),
            jwk("""{"kty":"OKP","crv":"P-256","x":"AQ"}"""),
            jwk("""{"kty":"OKP","crv":"Ed25519","x":"AQ","y":"Ag"}"""),
        ).forEach { invalid ->
            assertFailsWith<IllegalArgumentException> { invalid.toCoseKey() }
        }
    }

    @Test
    fun `JWK private material metadata must match contents`() {
        val publicWithPrivateKey = jwk(
            """{"kty":"EC","crv":"P-256","x":"AQ","y":"Ag","d":"Aw"}""",
            privateMaterial = false,
        )
        val privateWithoutPrivateKey = jwk(
            """{"kty":"EC","crv":"P-256","x":"AQ","y":"Ag"}""",
            privateMaterial = true,
        )
        val publicWithSecretField = jwk(
            """{"kty":"OKP","crv":"Ed25519","x":"AQ","k":"Aw"}""",
            privateMaterial = false,
        )

        listOf(publicWithPrivateKey, privateWithoutPrivateKey, publicWithSecretField).forEach { invalid ->
            assertFailsWith<IllegalArgumentException> { invalid.toCoseKey() }
        }

        assertContentEquals(
            byteArrayOf(3),
            jwk(
                """{"kty":"EC","crv":"P-256","x":"AQ","y":"Ag","d":"Aw"}""",
                privateMaterial = true,
            ).toCoseKey().d,
        )
    }

    private fun jwk(json: String, privateMaterial: Boolean = false) = EncodedKey.Jwk(
        data = BinaryData(json.encodeToByteArray()),
        privateMaterial = privateMaterial,
    )
}
