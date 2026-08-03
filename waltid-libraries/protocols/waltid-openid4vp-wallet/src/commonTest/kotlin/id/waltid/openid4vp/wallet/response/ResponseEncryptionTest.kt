package id.waltid.openid4vp.wallet.response

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
import kotlin.test.assertNull

class ResponseEncryptionTest {
    private val key = Json.parseToJsonElement(
        """{"kty":"EC","crv":"P-256","x":"y4ajD4aIXGiLGqiF81nN5HvBFvBEvrZcgFsp5VIJO30","y":"jyrZRfxKz113LQNg2x5f7Nu4fwW5Ov5gCzhPaTZuTCg","use":"enc","alg":"ECDH-ES","kid":"enc-key"}"""
    ).jsonObject

    @Test
    fun returnsNullForUnencryptedResponseModes() = runTest {
        assertNull(ResponseEncryption.resolve(request(OpenID4VPResponseMode.DIRECT_POST)))
        assertNull(ResponseEncryption.resolve(request(OpenID4VPResponseMode.FRAGMENT)))
    }

    @Test
    fun selectsKeyAndAlgorithmDeterministically() = runTest {
        val keyZ = JsonObject(key + ("kid" to JsonPrimitive("z-key")))
        val keyA = JsonObject(key + ("kid" to JsonPrimitive("a-key")))
        val first = ResponseEncryption.resolve(request(keys = listOf(keyZ, keyA), encValues = listOf("A128GCM", "A256GCM")))
        val second = ResponseEncryption.resolve(request(keys = listOf(keyA, keyZ), encValues = listOf("A256GCM", "A128GCM")))

        assertEquals("a-key", first?.keyId)
        assertEquals(first?.keyId, second?.keyId)
        assertEquals(first?.verifierKeyThumbprint, second?.verifierKeyThumbprint)
        assertEquals("A256GCM", first?.encAlgorithm)
        assertEquals(first?.encAlgorithm, second?.encAlgorithm)
    }

    @Test
    fun defaultsToA128GcmWhenVerifierOmitsEncAlgorithms() = runTest {
        assertEquals("A128GCM", ResponseEncryption.resolve(request())?.encAlgorithm)
    }

    @Test
    fun rejectsInvalidKeySetsAndAlgorithms() = runTest {
        val invalidKeys = listOf(
            JsonObject(key - "alg") to "missing alg",
            JsonObject(key - "kid") to "missing kid",
            JsonObject(key + ("kid" to JsonPrimitive(" "))) to "blank kid",
            JsonObject(key + ("kid" to JsonPrimitive(7))) to "non-string kid",
            JsonObject(key + ("crv" to JsonPrimitive("P-384"))) to "unsupported curve",
            JsonObject(key + ("use" to JsonPrimitive("sig"))) to "signing key",
        )
        invalidKeys.forEach { (invalid, description) ->
            assertFailsWith<IllegalArgumentException>(description) {
                ResponseEncryption.resolve(request(keys = listOf(invalid)))
            }
        }
        assertFailsWith<IllegalArgumentException>("duplicate kid") {
            ResponseEncryption.resolve(request(keys = listOf(key, key)))
        }
        assertFailsWith<IllegalArgumentException>("unsupported enc") {
            ResponseEncryption.resolve(request(encValues = listOf("A128CBC-HS256")))
        }
    }

    private fun request(
        responseMode: OpenID4VPResponseMode = OpenID4VPResponseMode.DIRECT_POST_JWT,
        keys: List<JsonObject> = listOf(key),
        encValues: List<String>? = null,
    ) = AuthorizationRequest(
        responseUri = "https://verifier.example/response",
        responseMode = responseMode,
        clientId = "verifier-client",
        clientMetadata = ClientMetadata(
            jwks = ClientMetadata.Jwks(keys),
            encryptedResponseEncValuesSupported = encValues,
        ),
    )
}
