package id.walt.verifier2.handlers.authrequest

import com.nimbusds.jose.JWEObject
import com.nimbusds.jose.crypto.ECDHDecrypter
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJwe
import id.walt.crypto2.jose.JweContentEncryption
import id.walt.crypto2.jose.exportPublicJwkObject
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Verifier2EncryptedRequestObjectTest {
    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())

    private suspend fun key(id: String) = runtime.generateSoftwareKey(GenerateSoftwareKeyRequest(
        id = KeyId(id), spec = KeySpec.Ec(EcCurve.P256), usages = setOf(KeyUsage.KEY_AGREEMENT),
    ))

    private fun metadata(keys: List<JsonObject>, enc: String = "A128GCM", alg: String = "ECDH-ES") = buildJsonObject {
        put("request_object_encryption_alg_values_supported", buildJsonArray { add(alg) })
        put("request_object_encryption_enc_values_supported", buildJsonArray { add(enc) })
        put("jwks", buildJsonObject { put("keys", JsonArray(keys)) })
    }.toString()

    @Test
    fun `encrypts to the usable wallet key using either supported content encryption`() = runTest {
        val recipient = ECKeyGenerator(Curve.P_256).keyID("wallet-key").generate()
        val publicJwk = buildJsonObject {
            Json.parseToJsonElement(recipient.toPublicJWK().toJSONString()).jsonObject
                .forEach { (name, value) -> put(name, value) }
            put("kid", "wallet-key")
            put("use", "enc")
        }
        val unusable = JsonObject(publicJwk + ("use" to JsonPrimitive("sig")))
        val invalidPoint = JsonObject(publicJwk + mapOf(
            "x" to JsonPrimitive("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"),
            "y" to JsonPrimitive("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"),
        ))
        for (encryption in JweContentEncryption.entries) {
            val encrypted = Verifier2RequestUriPostHandler.encryptRequestObject(
                "signed.request.object", metadata(listOf(unusable, invalidPoint, publicJwk), encryption.identifier),
            )
            // An independent JOSE implementation must decrypt the production verifier's output.
            val decrypted = JWEObject.parse(encrypted).apply { decrypt(ECDHDecrypter(recipient)) }
            assertEquals("signed.request.object", decrypted.payload.toString())
            assertEquals(encryption.identifier, decrypted.header.encryptionMethod.name)
            assertEquals("JWT", decrypted.header.contentType)
            assertEquals("wallet-key", decrypted.header.keyID)
        }
    }

    @Test
    fun `required encryption never falls back for missing keys or incompatible algorithms`() = runTest {
        val publicJwk = key("wallet-key").exportPublicJwkObject()
        for (metadata in listOf(null, "{}", metadata(emptyList()),
            metadata(listOf(publicJwk), alg = "RSA-OAEP"), metadata(listOf(publicJwk), enc = "A128CBC-HS256"),
            metadata(listOf(JsonObject(publicJwk + ("use" to JsonPrimitive("sig"))))),
            metadata(listOf(JsonObject(publicJwk + ("d" to JsonPrimitive("private"))))),
            metadata(listOf(JsonObject(publicJwk + ("key_ops" to buildJsonArray { add("sign") })))),
            metadata(listOf(JsonObject(publicJwk + ("x" to JsonPrimitive("invalid"))))),
            metadata(listOf(JsonObject(publicJwk + mapOf(
                "x" to JsonPrimitive("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"),
                "y" to JsonPrimitive("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"),
            )))),
        )) {
            assertFailsWith<IllegalArgumentException> {
                Verifier2RequestUriPostHandler.encryptRequestObject("signed.request.object", metadata)
            }
        }
    }

    @Test
    fun `another wallets key cannot decrypt the request`() = runTest {
        val encrypted = Verifier2RequestUriPostHandler.encryptRequestObject(
            "signed.request.object", metadata(listOf(key("recipient").exportPublicJwkObject())),
        )
        assertFailsWith<Exception> {
            CompactJwe.decrypt(encrypted, key("other"), setOf(JweContentEncryption.A128GCM))
        }
    }
}
