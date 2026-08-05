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
import id.walt.crypto2.keys.SoftwareKey
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
import kotlin.test.assertFails
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PrivateJwkConsistencyTest {
    private val provider = CryptographySoftwareKeyProvider()
    private val runtime = CryptoRuntime(listOf(provider))

    @Test
    fun `private JWK import rejects mutated private or public members`() = runTest {
        cases.filter { supportsPrivateJwkImport(it.spec) }.forEachIndexed { index, case ->
            val key = generate(case, "mutation-$index")
            val privateJwk = assertIs<EncodedKey.Jwk>(key.capabilities.privateKeyExporter!!.exportPrivateKey())

            assertFails {
                runtime.restore(key.storedKey.copy(material = privateJwk.mutate("d")))
            }
            assertFails {
                runtime.restore(key.storedKey.copy(material = privateJwk.mutate(case.publicMember)))
            }
        }
    }

    @Test
    fun `valid private JWK and PKCS8 round trips retain signing capability`() = runTest {
        cases.filter { supportsPrivateJwkImport(it.spec) }.forEachIndexed { index, case ->
            val key = generate(case, "round-trip-$index")
            val privateJwk = assertIs<EncodedKey.Jwk>(key.capabilities.privateKeyExporter!!.exportPrivateKey())
            assertSigns(assertIs<SoftwareKey>(runtime.restore(key.storedKey.copy(material = privateJwk))), case.algorithm)

            if (supportsPkcs8Import(case.spec)) {
                val pkcs8 = assertIs<EncodedKey.Pkcs8Der>(
                    key.capabilities.privateKeyExporter!!.exportPrivateKey(KeyEncodingFormat.PKCS8_DER),
                )
                assertSigns(assertIs<SoftwareKey>(runtime.restore(key.storedKey.copy(material = pkcs8))), case.algorithm)
            }
        }
    }

    private suspend fun generate(case: Case, id: String): SoftwareKey = runtime.generateSoftwareKey(
        GenerateSoftwareKeyRequest(
            id = KeyId(id),
            spec = case.spec,
            usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        ),
    )

    private suspend fun assertSigns(key: SoftwareKey, algorithm: SignatureAlgorithm) {
        val message = "private-jwk-consistency".encodeToByteArray()
        val signature = key.capabilities.signer!!.sign(message, algorithm)
        assertTrue(key.capabilities.verifier!!.verify(message, signature, algorithm))
    }

    private fun supportsPrivateJwkImport(spec: KeySpec): Boolean = provider.supports(
        CryptoRequirement(
            operation = CryptoOperation.IMPORT_KEY,
            spec = spec,
            usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            keyEncoding = KeyEncodingFormat.JWK,
        ),
    )

    private fun supportsPkcs8Import(spec: KeySpec): Boolean = provider.supports(
        CryptoRequirement(
            operation = CryptoOperation.IMPORT_KEY,
            spec = spec,
            usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            keyEncoding = KeyEncodingFormat.PKCS8_DER,
        ),
    )

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

    private data class Case(
        val spec: KeySpec,
        val algorithm: SignatureAlgorithm,
        val publicMember: String,
    )

    private companion object {
        val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
        val cases = listOf(
            Case(
                KeySpec.Ec(EcCurve.P256),
                SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256),
                publicMember = "x",
            ),
            Case(
                KeySpec.Edwards(EdwardsCurve.ED25519),
                SignatureAlgorithm.EdDsa,
                publicMember = "x",
            ),
            Case(
                KeySpec.Rsa(2048),
                SignatureAlgorithm.RsaPkcs1(DigestAlgorithm.SHA_256),
                publicMember = "n",
            ),
        )
    }
}
