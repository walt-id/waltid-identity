package id.walt.crypto2.keys

import id.walt.crypto2.algorithms.AsymmetricEncryptionAlgorithm
import id.walt.crypto2.algorithms.AeadAlgorithm
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.KeyWrappingAlgorithm
import id.walt.crypto2.serialization.BinaryData
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class CiphertextSerializationTest {
    @Test
    fun `raw and provider opaque ciphertext round trip`() {
        val algorithm = AsymmetricEncryptionAlgorithm.RsaOaep(DigestAlgorithm.SHA_256)
        val values = listOf<AsymmetricCiphertext>(
            AsymmetricCiphertext.Raw(algorithm, BinaryData(byteArrayOf(1, 2))),
            AsymmetricCiphertext.Opaque(
                algorithm = algorithm,
                provider = ProviderId("aws-kms"),
                keyId = KeyId("key-id"),
                blob = BinaryData(byteArrayOf(3, 4)),
                keyVersion = "2",
                context = mapOf("tenant" to "example"),
                providerData = BinaryData(byteArrayOf(5)),
            ),
        )

        values.forEach { value ->
            assertEquals(value, Json.decodeFromString<AsymmetricCiphertext>(Json.encodeToString(value)))
        }
    }

    @Test
    fun `wrapped key preserves mechanism and provider identity`() {
        val wrapped = WrappedKey.Opaque(
            algorithm = KeyWrappingAlgorithm.BuiltIn("A256KW"),
            blob = BinaryData(byteArrayOf(1, 2, 3)),
            wrappedKeySpec = KeySpec.Symmetric(SymmetricKeyType.AES, 256),
            provider = ProviderId("pkcs11"),
            wrappingKeyId = KeyId("wrapping-key"),
            keyVersion = "4",
        )

        assertEquals(wrapped, Json.decodeFromString<WrappedKey>(Json.encodeToString<WrappedKey>(wrapped)))
    }

    @Test
    fun `encoded key material rejects mismatched specs and private flags`() {
        fun jwk(value: String, privateMaterial: Boolean) = EncodedKey.Jwk(
            BinaryData(value.encodeToByteArray()),
            privateMaterial,
        )

        assertFails {
            EncodedKeyMaterial(
                KeySpec.Ec(EcCurve.P256),
                jwk("""{"kty":"OKP","crv":"Ed25519","x":"AQ"}""", false),
            )
        }
        assertFails {
            EncodedKeyMaterial(
                KeySpec.Symmetric(SymmetricKeyType.AES, 256),
                jwk("""{"kty":"oct","k":"AAAAAAAAAAAAAAAAAAAAAA"}""", true),
            )
        }
        assertFails {
            EncodedKeyMaterial(
                KeySpec.Symmetric(SymmetricKeyType.AES, 256),
                jwk("""{"kty":"oct","k":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"}""", false),
            )
        }
    }

    @Test
    fun `AEAD and HPKE composition results preserve their framing`() {
        val aead = AeadCiphertext(
            algorithm = AeadAlgorithm.AesGcm(256),
            nonce = BinaryData(ByteArray(12) { 1 }),
            ciphertext = BinaryData(byteArrayOf(2, 3)),
            authenticationTag = BinaryData(ByteArray(16) { 4 }),
        )
        val hpke = HpkeCiphertext(
            suite = HpkeSuite(
                kem = HpkeKemId.DHKEM_P256_HKDF_SHA256,
                kdf = HpkeKdfId.HKDF_SHA256,
                aead = HpkeAeadId.AES_128_GCM,
            ),
            encapsulatedKey = BinaryData(ByteArray(65) { index -> if (index == 0) 0x04 else 5 }),
            ciphertext = BinaryData(ByteArray(16) { 6 }),
        )

        assertEquals(aead, Json.decodeFromString<AeadCiphertext>(Json.encodeToString(aead)))
        assertEquals(hpke, Json.decodeFromString<HpkeCiphertext>(Json.encodeToString(hpke)))
        assertFails {
            HpkeCiphertext(
                suite = hpke.suite,
                encapsulatedKey = BinaryData(byteArrayOf(1)),
                ciphertext = hpke.ciphertext,
            )
        }
    }
}
