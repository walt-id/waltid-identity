package id.walt.crypto2.providers.cryptography

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.KeyAgreementAlgorithm
import id.walt.crypto2.hpke.Hpke
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.HpkeCiphertext
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeyEncodingFormat
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.keys.toPublicJwk
import id.walt.crypto2.keys.toStoredSoftwareKey
import id.walt.crypto2.providers.CryptoOperation
import id.walt.crypto2.providers.CryptoRequirement
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.serialization.BinaryData
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * An EC public key whose point is not on its curve must be refused wherever crypto2 decodes one, on every platform.
 *
 * The JDK's EC KeyFactory stores whatever coordinates it is given, so without crypto2's own check such a key restored
 * on the JVM and only failed later: as a signature that never verifies, or as a peer key handed to ECDH. The fixture
 * is a real P-256 point with the last bit of Y flipped, which takes it off the curve.
 */
class OffCurvePublicKeyTest {
    private val provider = CryptographySoftwareKeyProvider()
    private val runtime = CryptoRuntime(listOf(provider))
    private val spec = KeySpec.Ec(EcCurve.P256)

    @Test
    fun `the unmodified fixture is accepted`() = runTest {
        // Control: proves the rejections below come from the flipped bit, not from the fixture or its encodings.
        runtime.restore(stored(publicJwk(ON_CURVE_POINT)))
        runtime.restore(stored(spki(ON_CURVE_SPKI)))
        assertEquals(publicJwk(ON_CURVE_POINT).json(), spki(ON_CURVE_SPKI).toPublicJwk(spec).json())
    }

    @Test
    fun `restoring an off-curve public JWK is rejected`() = runTest {
        assertFailsWith<IllegalArgumentException> { runtime.restore(stored(publicJwk(OFF_CURVE_POINT))) }
    }

    @Test
    fun `restoring an off-curve SPKI is rejected`() = runTest {
        assertFailsWith<IllegalArgumentException> { runtime.restore(stored(spki(OFF_CURVE_SPKI))) }
    }

    @Test
    fun `persisting an off-curve JWK is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            publicJwk(OFF_CURVE_POINT).toStoredSoftwareKey(KeyId("off-curve"), setOf(KeyUsage.VERIFY))
        }
    }

    @Test
    fun `converting an off-curve SPKI is rejected`() = runTest {
        assertFailsWith<IllegalArgumentException> { spki(OFF_CURVE_SPKI).toPublicJwk(spec) }
    }

    @Test
    fun `ECDH refuses an off-curve peer key`() = runTest {
        val key = agreementKey() ?: return@runTest
        val agreement = assertNotNull(key.capabilities.keyAgreement)

        assertFailsWith<IllegalArgumentException> {
            agreement.generateSharedSecret(publicJwk(OFF_CURVE_POINT), KeyAgreementAlgorithm.Ecdh)
        }
        assertFailsWith<IllegalArgumentException> {
            agreement.generateSharedSecret(spki(OFF_CURVE_SPKI), KeyAgreementAlgorithm.Ecdh)
        }
    }

    @Test
    fun `HPKE refuses an off-curve recipient key`() = runTest {
        assertFailsWith<IllegalArgumentException> { Hpke.validateRecipientPublicKey(publicJwk(OFF_CURVE_POINT)) }
        assertFailsWith<IllegalArgumentException> {
            Hpke.sealBase(publicJwk(OFF_CURVE_POINT), "plaintext".encodeToByteArray())
        }
    }

    @Test
    fun `HPKE refuses an off-curve encapsulated key`() = runTest {
        val recipient = agreementKey() ?: return@runTest
        val ciphertext = HpkeCiphertext(
            suite = Hpke.P256_HKDF_SHA256_AES_128_GCM,
            encapsulatedKey = BinaryData(Base64.decode(OFF_CURVE_POINT)),
            ciphertext = BinaryData(ByteArray(32)),
        )

        assertFailsWith<IllegalArgumentException> { Hpke.openBase(recipient, ciphertext) }
    }

    private suspend fun agreementKey(): Key? {
        val usages = setOf(KeyUsage.KEY_AGREEMENT)
        val generate = CryptoRequirement(
            operation = CryptoOperation.GENERATE_KEY,
            spec = spec,
            usages = usages,
            keyEncoding = KeyEncodingFormat.JWK,
        )
        if (!provider.supports(generate)) {
            println("  skipped: this platform cannot generate a P-256 key-agreement key")
            return null
        }
        return runtime.generateSoftwareKey(GenerateSoftwareKeyRequest(id = KeyId("agreement"), spec = spec, usages = usages))
    }

    private fun stored(material: EncodedKey) = StoredKey.Software(
        version = StoredKey.CURRENT_VERSION,
        id = KeyId("fixture"),
        spec = spec,
        usages = setOf(KeyUsage.VERIFY),
        material = material,
    )

    private fun spki(base64: String) = EncodedKey.SpkiDer(BinaryData(Base64.decode(base64)))

    // The point members only: member order differs between encoders, and WebCrypto's export adds "ext".
    private fun EncodedKey.Jwk.json() = Json.parseToJsonElement(data.toByteArray().decodeToString()).jsonObject
        .filterKeys { it in setOf("kty", "crv", "x", "y") }

    private fun publicJwk(base64Point: String): EncodedKey.Jwk {
        val point = Base64.decode(base64Point)
        val x = base64Url.encode(point.copyOfRange(1, 33))
        val y = base64Url.encode(point.copyOfRange(33, 65))
        return EncodedKey.Jwk(
            BinaryData("""{"crv":"P-256","kty":"EC","x":"$x","y":"$y"}""".encodeToByteArray()),
            privateMaterial = false,
        )
    }

    private companion object {
        val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

        // The P-256 key CompressedPointSpkiSupportTest uses, exported by OpenSSL.
        const val ON_CURVE_POINT =
            "BGIJyM2XMySEjI4vTuzuyQrriJbO67204xMNF40iqLRo2Rq22dZjsMSQfqPhoT4KYM3dbG9M7M6eTOEyYSRG2iY="
        const val ON_CURVE_SPKI =
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEYgnIzZczJISMji9O7O7JCuuIls7rvbTjEw0XjSKotGjZGrbZ1mOw" +
                "xJB+o+GhPgpgzd1sb0zszp5M4TJhJEbaJg=="

        // The same encodings with the last bit of Y flipped (last byte 0x26 -> 0x27), checked off the curve.
        const val OFF_CURVE_POINT =
            "BGIJyM2XMySEjI4vTuzuyQrriJbO67204xMNF40iqLRo2Rq22dZjsMSQfqPhoT4KYM3dbG9M7M6eTOEyYSRG2ic="
        const val OFF_CURVE_SPKI =
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEYgnIzZczJISMji9O7O7JCuuIls7rvbTjEw0XjSKotGjZGrbZ1mOw" +
                "xJB+o+GhPgpgzd1sb0zszp5M4TJhJEbaJw=="
    }
}
