@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.waltid.openid4vp.wallet.response

import id.walt.crypto2.jose.JweContentEncryption
import id.walt.crypto2.jose.Jwk
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResponseEncryptionTest {
    private val publicKey = Json.parseToJsonElement(
        """{
            "kty":"EC",
            "crv":"P-256",
            "x":"y4ajD4aIXGiLGqiF81nN5HvBFvBEvrZcgFsp5VIJO30",
            "y":"jyrZRfxKz113LQNg2x5f7Nu4fwW5Ov5gCzhPaTZuTCg",
            "use":"enc",
            "kid":"enc-key",
            "alg":"ECDH-ES"
        }"""
    ).jsonObject

    @Test
    fun `selects final compliant encryption metadata`() = runTest {
        val config = requireNotNull(ResponseEncryption.resolveCrypto2(request(publicKey)))

        assertEquals(JweContentEncryption.A256GCM, config.contentEncryption)
        assertFalse(config.recipientPublicKey.privateMaterial)
        assertEquals(Jwk.sha256Thumbprint(config.recipientPublicKey), config.thumbprint())

        val metadata = config.metadata()
        assertEquals("ECDH-ES", metadata.keyManagementAlgorithm)
        assertEquals("A256GCM", metadata.contentEncryptionAlgorithm)
        assertEquals("enc-key", metadata.verifierKeyId)
        assertEquals("ds5PaVMO_C5Ig-uE8M4pwTsYdA9LLbT2D8mHERDXudE", metadata.verifierKeyThumbprint)
    }

    @Test
    fun `returns no encryption metadata for an unencrypted response`() = runTest {
        val request = request(publicKey).copy(responseMode = OpenID4VPResponseMode.DIRECT_POST)

        assertNull(ResponseEncryption.resolveCrypto2(request))
    }

    @Test
    fun `rejects encryption key without alg`() = runTest {
        val keyWithoutAlgorithm = JsonObject(publicKey - "alg")

        assertFailsWith<IllegalArgumentException> {
            ResponseEncryption.resolveCrypto2(request(keyWithoutAlgorithm))
        }
    }

    @Test
    fun `rejects private recipient material`() = runTest {
        val privateKey = JsonObject(publicKey + ("d" to JsonPrimitive("AQ")))

        val error = assertFailsWith<IllegalArgumentException> {
            ResponseEncryption.resolveCrypto2(request(privateKey))
        }

        assertTrue(error.message.orEmpty().contains("private material"))
    }

    @Test
    fun `selects the same key independent of verifier jwks order`() = runTest {
        val keyZ = JsonObject(publicKey + ("kid" to JsonPrimitive("z-key")))
        val keyA = JsonObject(publicKey + ("kid" to JsonPrimitive("a-key")))

        val forward = requireNotNull(ResponseEncryption.resolveCrypto2(requestWithKeys(listOf(keyZ, keyA))))
        val reverse = requireNotNull(ResponseEncryption.resolveCrypto2(requestWithKeys(listOf(keyA, keyZ))))

        assertEquals("a-key", forward.metadata().verifierKeyId)
        assertEquals(forward.thumbprint(), reverse.thumbprint())
        assertEquals(forward.metadata().verifierKeyId, reverse.metadata().verifierKeyId)
    }

    @Test
    fun `rejects duplicate key ids and unsupported key metadata`() = runTest {
        val duplicateKid = JsonObject(publicKey + ("kid" to JsonPrimitive("enc-key")))
        assertFailsWith<IllegalArgumentException> {
            ResponseEncryption.resolveCrypto2(requestWithKeys(listOf(publicKey, duplicateKid)))
        }

        val signingKey = JsonObject(publicKey + ("use" to JsonPrimitive("sig")))
        assertFailsWith<IllegalArgumentException> {
            ResponseEncryption.resolveCrypto2(request(signingKey))
        }
    }

    @Suppress("DEPRECATION")
    @Test
    fun `legacy config remains public only`() = runTest {
        val config = requireNotNull(ResponseEncryption.resolve(request(publicKey)))

        assertEquals("A256GCM", config.encryptionMethod)
        assertFalse(Jwk.containsPrivateMaterial(config.key.exportJWKObject()))
    }

    private fun request(key: JsonObject) = AuthorizationRequest(
        clientId = "x509_hash:test",
        responseUri = "https://verifier.example/response",
        responseMode = OpenID4VPResponseMode.DIRECT_POST_JWT,
        clientMetadata = ClientMetadata(
            jwks = ClientMetadata.Jwks(listOf(key)),
            encryptedResponseEncValuesSupported = listOf("A256GCM"),
        ),
    )

    private fun requestWithKeys(keys: List<JsonObject>) = AuthorizationRequest(
        clientId = "x509_hash:test",
        responseUri = "https://verifier.example/response",
        responseMode = OpenID4VPResponseMode.DIRECT_POST_JWT,
        clientMetadata = ClientMetadata(
            jwks = ClientMetadata.Jwks(keys),
            encryptedResponseEncValuesSupported = listOf("A256GCM"),
        ),
    )
}
