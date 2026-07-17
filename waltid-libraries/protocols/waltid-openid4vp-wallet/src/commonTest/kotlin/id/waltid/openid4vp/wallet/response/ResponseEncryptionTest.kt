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
        val config = ResponseEncryption.resolve(request(publicKey))

        assertEquals("A256GCM", config?.encryptionMethod)
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
