package id.walt.crypto2.jose

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.Payload
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.RSAKey
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.SoftwareKey
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class NimbusJwsInteropTest {
    private val runtime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))

    @Test
    fun `Nimbus verifies crypto2 ES256 signatures`() = runTest {
        val key = generateKey()
        val signed = CompactJws.sign(
            payload = "interop".encodeToByteArray(),
            key = key,
            algorithm = JwsAlgorithm.ES256,
            protectedHeader = JsonObject(mapOf("typ" to JsonPrimitive("JWT"))),
        )
        val nimbus = JWSObject.parse(signed)

        assertTrue(nimbus.verify(ECDSAVerifier(key.toNimbusJwk().toPublicJWK())))
    }

    @Test
    fun `crypto2 verifies Nimbus ES256 signatures`() = runTest {
        val key = generateKey()
        val nimbus = JWSObject(
            JWSHeader.Builder(JWSAlgorithm.ES256).type(JOSEObjectType.JWT).build(),
            Payload("nimbus-interop"),
        )
        nimbus.sign(ECDSASigner(key.toNimbusJwk()))

        val verified = CompactJws.verify(nimbus.serialize(), key, JwsAlgorithm.ES256)

        assertContentEquals("nimbus-interop".encodeToByteArray(), verified.payload)
    }

    @Test
    fun `Nimbus and crypto2 interoperate for RS256 and PS256`() = runTest {
        listOf(
            JwsAlgorithm.RS256 to JWSAlgorithm.RS256,
            JwsAlgorithm.PS256 to JWSAlgorithm.PS256,
        ).forEach { (crypto2Algorithm, nimbusAlgorithm) ->
            val key = generateRsaKey(crypto2Algorithm)
            val jwk = key.toNimbusRsaJwk()

            val crypto2Signed = CompactJws.sign("crypto2".encodeToByteArray(), key, crypto2Algorithm)
            assertTrue(JWSObject.parse(crypto2Signed).verify(RSASSAVerifier(jwk.toPublicJWK())))

            val nimbusSigned = JWSObject(JWSHeader(nimbusAlgorithm), Payload("nimbus"))
            nimbusSigned.sign(RSASSASigner(jwk))
            assertContentEquals(
                "nimbus".encodeToByteArray(),
                CompactJws.verify(nimbusSigned.serialize(), key, crypto2Algorithm).payload,
            )
        }
    }

    private suspend fun generateKey(): SoftwareKey =
        runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("interop-key"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            ),
        )

    private suspend fun generateRsaKey(algorithm: JwsAlgorithm): SoftwareKey =
        runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("rsa-interop-key"),
                spec = KeySpec.Rsa(2048),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            ),
        )

    private fun SoftwareKey.toNimbusJwk(): ECKey {
        val material = storedKey.material as EncodedKey.Jwk
        return ECKey.parse(material.data.toByteArray().decodeToString())
    }

    private fun SoftwareKey.toNimbusRsaJwk(): RSAKey {
        val material = storedKey.material as EncodedKey.Jwk
        return RSAKey.parse(material.data.toByteArray().decodeToString())
    }
}
