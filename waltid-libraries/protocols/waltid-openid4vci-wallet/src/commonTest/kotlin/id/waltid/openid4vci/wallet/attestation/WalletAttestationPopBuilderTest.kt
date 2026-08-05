package id.waltid.openid4vci.wallet.attestation

import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.EdwardsCurve
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WalletAttestationPopBuilderTest {
    private val builder = WalletAttestationPopBuilder()

    @Test
    fun createsVerifiableEs256Pop() = runTest {
        val key = attestationTestKey("pop-p256")
        val jwt = builder.buildPopJwt(key, "wallet-client", "https://issuer.example.com/token")
        val verified = CompactJws.verify(jwt, key, JwsAlgorithm.ES256)
        val payload = Json.parseToJsonElement(verified.payload.decodeToString()).jsonObject
        val header = CompactJws.decodeUnverified(jwt).protectedHeader

        assertEquals("oauth-client-attestation-pop+jwt", header["typ"]?.jsonPrimitive?.content)
        assertEquals("ES256", header["alg"]?.jsonPrimitive?.content)
        assertEquals("wallet-client", payload["iss"]?.jsonPrimitive?.content)
        assertEquals("https://issuer.example.com/token", payload["aud"]?.jsonPrimitive?.content)
        assertNotNull(payload["jti"])
        val iat = payload.getValue("iat").jsonPrimitive.content.toLong()
        val exp = payload.getValue("exp").jsonPrimitive.content.toLong()
        assertEquals(300L, exp - iat)
    }

    @Test
    fun rejectsNonP256AndNonSigningKeys() = runTest {
        val ed25519 = attestationTestKey("pop-ed", KeySpec.Edwards(EdwardsCurve.ED25519))
        val verifyOnly = attestationTestKey("pop-verify", usages = setOf(KeyUsage.KEY_AGREEMENT))

        assertTrue(assertFailsWith<IllegalStateException> {
            builder.buildPopJwt(ed25519, "client", "https://issuer.example.com/token")
        }.message.orEmpty().contains("P-256"))
        assertFailsWith<IllegalArgumentException> {
            builder.buildPopJwt(verifyOnly, "client", "https://issuer.example.com/token")
        }
    }
}
