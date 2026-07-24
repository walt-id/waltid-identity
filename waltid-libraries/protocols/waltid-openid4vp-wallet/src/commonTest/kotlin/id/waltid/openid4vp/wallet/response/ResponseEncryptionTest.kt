@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.waltid.openid4vp.wallet.response

import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

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
    fun `selects response encryption metadata`() = runTest {
        val config = ResponseEncryption.resolve(request(publicKey))

        val metadata = config?.metadata()
        assertEquals("ECDH-ES", metadata?.keyManagementAlgorithm)
        assertEquals("A256GCM", metadata?.contentEncryptionAlgorithm)
        assertEquals("enc-key", metadata?.verifierKeyId)
        assertEquals("ds5PaVMO_C5Ig-uE8M4pwTsYdA9LLbT2D8mHERDXudE", metadata?.verifierKeyThumbprint)
    }

    @Test
    fun `returns no encryption metadata for an unencrypted response`() = runTest {
        val request = request(publicKey).copy(responseMode = OpenID4VPResponseMode.DIRECT_POST)

        assertNull(ResponseEncryption.resolve(request))
    }

    @Test
    fun `rejects encryption key without alg`() = runTest {
        val keyWithoutAlgorithm = kotlinx.serialization.json.JsonObject(publicKey - "alg")

        assertFailsWith<IllegalArgumentException> {
            ResponseEncryption.resolve(request(keyWithoutAlgorithm))
        }
    }

    private fun request(key: kotlinx.serialization.json.JsonObject) = AuthorizationRequest(
        clientId = "x509_hash:test",
        responseUri = "https://verifier.example/response",
        responseMode = OpenID4VPResponseMode.DIRECT_POST_JWT,
        clientMetadata = ClientMetadata(
            jwks = ClientMetadata.Jwks(listOf(key)),
            encryptedResponseEncValuesSupported = listOf("A256GCM"),
        ),
    )
}
