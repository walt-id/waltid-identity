package id.walt.crypto2.providers.cryptography

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EdwardsCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyEncodingFormat
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.keys.decodePrivateKeyPem
import id.walt.crypto2.providers.CryptoOperation
import id.walt.crypto2.providers.CryptoRequirement
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.serialization.BinaryData
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AndroidCryptographyFormatCapabilitiesTest {
    private val provider = CryptographySoftwareKeyProvider()
    private val runtime = CryptoRuntime(listOf(provider))

    @Test
    fun `private DER is rejected by capabilities before generation or import`() = runTest {
        assertEquals(setOf(KeyEncodingFormat.JWK), CryptographyCapabilityProfile.Portable.keyGenerationFormats)
        assertEquals(
            setOf(KeyEncodingFormat.JWK, KeyEncodingFormat.SPKI_DER),
            CryptographyCapabilityProfile.Portable.keyImportFormats,
        )
        assertEquals(
            setOf(KeyEncodingFormat.JWK, KeyEncodingFormat.SPKI_DER),
            CryptographyCapabilityProfile.Portable.publicKeyExportFormats,
        )
        assertEquals(setOf(KeyEncodingFormat.JWK), CryptographyCapabilityProfile.Portable.privateKeyExportFormats)
        assertEquals(
            setOf(KeySpec.Rsa(2048), KeySpec.Rsa(3072), KeySpec.Rsa(4096)),
            CryptographyCapabilityProfile.Portable.privateJwkValidationSpecs,
        )
        val attemptedOverride = CryptographySoftwareKeyProvider(
            profile = CryptographyCapabilityProfile.Portable.copy(
                keyGenerationFormats = setOf(KeyEncodingFormat.JWK, KeyEncodingFormat.PKCS8_DER),
                keyImportFormats = KeyEncodingFormat.entries.toSet(),
                privateKeyExportFormats = setOf(KeyEncodingFormat.JWK, KeyEncodingFormat.PKCS8_DER),
            ),
        )
        val parsedPkcs8 = "-----BEGIN PRIVATE KEY-----\nMA==\n-----END PRIVATE KEY-----\n".decodePrivateKeyPem()
        assertContentEquals(byteArrayOf(0x30), parsedPkcs8.data.toByteArray())

        listOf(
            KeySpec.Ec(EcCurve.P256),
            KeySpec.Edwards(EdwardsCurve.ED25519),
        ).forEach { spec ->
            val generation = CryptoRequirement(
                operation = CryptoOperation.GENERATE_KEY,
                spec = spec,
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
                keyEncoding = KeyEncodingFormat.PKCS8_DER,
            )
            val import = generation.copy(operation = CryptoOperation.IMPORT_KEY)
            val privateExport = generation.copy(operation = CryptoOperation.EXPORT_PRIVATE)

            assertFalse(provider.supports(generation))
            assertFalse(provider.supports(import))
            assertFalse(provider.supports(privateExport))
            assertFalse(attemptedOverride.supports(generation))
            assertFalse(attemptedOverride.supports(import))
            assertFalse(attemptedOverride.supports(privateExport))
            assertEquals(
                "Unsupported software-key generation request",
                assertFailsWith<IllegalArgumentException> {
                    provider.generate(
                        GenerateSoftwareKeyRequest(
                            id = KeyId("unsupported-generation"),
                            spec = spec,
                            usages = generation.usages,
                            keyEncoding = KeyEncodingFormat.PKCS8_DER,
                        ),
                    )
                }.message,
            )
            assertEquals(
                "Unsupported stored software key",
                assertFailsWith<IllegalArgumentException> {
                    provider.restore(
                        StoredKey.Software(
                            version = StoredKey.CURRENT_VERSION,
                            id = KeyId("unsupported-import"),
                            spec = spec,
                            usages = import.usages,
                            material = parsedPkcs8,
                        ),
                    )
                }.message,
            )
        }
    }

    @Test
    fun `JWK operations and SPKI public import export remain supported`() = runTest {
        exerciseSupportedFormats(
            spec = KeySpec.Ec(EcCurve.P256),
            algorithm = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256),
        )
        exerciseSupportedFormats(
            spec = KeySpec.Edwards(EdwardsCurve.ED25519),
            algorithm = SignatureAlgorithm.EdDsa,
        )
    }

    @Test
    fun `RSA private JWK validation accepts matching material and rejects mismatches`() = runTest {
        val spec = KeySpec.Rsa(2048)
        val usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY)
        val key = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(KeyId("android-rsa-validation"), spec, usages),
        )
        val privateJwk = assertIs<EncodedKey.Jwk>(key.capabilities.privateKeyExporter!!.exportPrivateKey())
        assertTrue(
            provider.supports(
                CryptoRequirement(
                    operation = CryptoOperation.IMPORT_KEY,
                    spec = spec,
                    usages = usages,
                    keyEncoding = KeyEncodingFormat.JWK,
                ),
            ),
        )
        val restored = runtime.restore(key.storedKey.copy(material = privateJwk))
        val message = "android-rsa-validation".encodeToByteArray()
        val algorithm = SignatureAlgorithm.RsaPkcs1(DigestAlgorithm.SHA_256)
        val signature = restored.capabilities.signer!!.sign(message, algorithm)
        assertTrue(restored.capabilities.verifier!!.verify(message, signature, algorithm))
        assertFailsWith<IllegalArgumentException> {
            runtime.restore(key.storedKey.copy(material = privateJwk.mutate("d")))
        }
        assertFailsWith<IllegalArgumentException> {
            runtime.restore(key.storedKey.copy(material = privateJwk.mutate("n")))
        }
    }

    private suspend fun exerciseSupportedFormats(spec: KeySpec, algorithm: SignatureAlgorithm) {
        val usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY)
        assertTrue(
            provider.supports(
                CryptoRequirement(
                    operation = CryptoOperation.GENERATE_KEY,
                    spec = spec,
                    usages = usages,
                    keyEncoding = KeyEncodingFormat.JWK,
                ),
            ),
        )
        val privateKey = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("supported-${spec.hashCode()}"),
                spec = spec,
                usages = usages,
            ),
        )
        val privateImport = CryptoRequirement(
            operation = CryptoOperation.IMPORT_KEY,
            spec = spec,
            usages = usages,
            keyEncoding = KeyEncodingFormat.JWK,
        )
        assertFalse(provider.supports(privateImport))
        assertEquals(
            "Unsupported stored software key",
            assertFailsWith<IllegalArgumentException> { provider.restore(privateKey.storedKey) }.message,
        )
        assertIs<EncodedKey.Jwk>(assertNotNull(privateKey.capabilities.privateKeyExporter).exportPrivateKey())
        assertEquals(
            "Private key export format is not supported: PKCS8_DER",
            assertFailsWith<IllegalArgumentException> {
                privateKey.capabilities.privateKeyExporter!!.exportPrivateKey(KeyEncodingFormat.PKCS8_DER)
            }.message,
        )

        val spki = assertIs<EncodedKey.SpkiDer>(
            assertNotNull(privateKey.capabilities.publicKeyExporter).exportPublicKey(KeyEncodingFormat.SPKI_DER),
        )
        val publicKey = runtime.restore(
            privateKey.storedKey.copy(
                usages = setOf(KeyUsage.VERIFY),
                material = spki,
            ),
        )
        val message = "android-format-capability".encodeToByteArray()
        val signature = assertNotNull(privateKey.capabilities.signer).sign(message, algorithm)
        assertTrue(assertNotNull(publicKey.capabilities.verifier).verify(message, signature, algorithm))
    }

    private fun EncodedKey.Jwk.mutate(name: String): EncodedKey.Jwk {
        val json = Json.parseToJsonElement(data.toByteArray().decodeToString()) as JsonObject
        val value = base64Url.decode(json.getValue(name).jsonPrimitive.content)
        value[value.lastIndex] = (value.last().toInt() xor 1).toByte()
        return copy(
            data = BinaryData(
                Json.encodeToString(JsonObject(json + (name to JsonPrimitive(base64Url.encode(value)))))
                    .encodeToByteArray(),
            ),
        )
    }

    private companion object {
        val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
    }
}
