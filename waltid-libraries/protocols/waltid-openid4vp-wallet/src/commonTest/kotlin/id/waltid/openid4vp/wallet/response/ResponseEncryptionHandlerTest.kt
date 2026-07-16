package id.waltid.openid4vp.wallet.response

import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResponseEncryptionHandlerTest {

    // Valid EC P-256 public key for testing encryption config extraction
    // Uses coordinates from walt.id test suite
    private val testEcPublicKeyJwk = Json.parseToJsonElement("""
        {
            "kty": "EC",
            "crv": "P-256",
            "x": "y4ajD4aIXGiLGqiF81nN5HvBFvBEvrZcgFsp5VIJO30",
            "y": "jyrZRfxKz113LQNg2x5f7Nu4fwW5Ov5gCzhPaTZuTCg",
            "use": "enc",
            "alg": "ECDH-ES",
            "kid": "test-enc-key-1"
        }
    """.trimIndent()).jsonObject

    @Test
    fun extractEncryptionConfig_returnsNull_whenResponseModeIsDirectPost() = runTest {
        val authRequest = AuthorizationRequest(
            responseUri = "https://verifier.example/response",
            responseMode = OpenID4VPResponseMode.DIRECT_POST,
            clientId = "verifier-client",
        )

        val result = ResponseEncryptionHandler.extractEncryptionConfig(authRequest).getOrThrow()

        assertNull(result, "Should return null for non-encrypted response modes")
    }

    @Test
    fun extractEncryptionConfig_returnsNull_whenResponseModeIsFragment() = runTest {
        val authRequest = AuthorizationRequest(
            redirectUri = "https://wallet.example/callback",
            responseMode = OpenID4VPResponseMode.FRAGMENT,
        )

        val result = ResponseEncryptionHandler.extractEncryptionConfig(authRequest).getOrThrow()

        assertNull(result, "Should return null for fragment response mode")
    }

    @Test
    fun extractEncryptionConfig_returnsConfig_whenResponseModeIsDirectPostJwt() = runTest {
        val clientMetadata = ClientMetadata(
            jwks = ClientMetadata.Jwks(keys = listOf(testEcPublicKeyJwk)),
            encryptedResponseEncValuesSupported = listOf("A256GCM"),
        )
        val authRequest = AuthorizationRequest(
            responseUri = "https://verifier.example/response",
            responseMode = OpenID4VPResponseMode.DIRECT_POST_JWT,
            clientId = "verifier-client",
            clientMetadata = clientMetadata,
        )

        val result = ResponseEncryptionHandler.extractEncryptionConfig(authRequest).getOrThrow()

        assertNotNull(result)
        assertEquals("A256GCM", result.encAlgorithm)
        assertEquals("ECDH-ES", result.algAlgorithm)
        assertEquals("test-enc-key-1", result.keyId)
    }

    @Test
    fun extractEncryptionConfig_usesDefaultEncAlg_whenNotSpecifiedInMetadata() = runTest {
        val clientMetadata = ClientMetadata(
            jwks = ClientMetadata.Jwks(keys = listOf(testEcPublicKeyJwk)),
        )
        val authRequest = AuthorizationRequest(
            responseUri = "https://verifier.example/response",
            responseMode = OpenID4VPResponseMode.DIRECT_POST_JWT,
            clientId = "verifier-client",
            clientMetadata = clientMetadata,
        )

        val result = ResponseEncryptionHandler.extractEncryptionConfig(authRequest).getOrThrow()

        assertNotNull(result)
        assertEquals("A128GCM", result.encAlgorithm, "Should default to A128GCM per spec")
    }

    @Test
    fun extractEncryptionConfig_fails_whenClientMetadataIsMissing() = runTest {
        val authRequest = AuthorizationRequest(
            responseUri = "https://verifier.example/response",
            responseMode = OpenID4VPResponseMode.DIRECT_POST_JWT,
            clientId = "verifier-client",
            clientMetadata = null,
        )

        val result = ResponseEncryptionHandler.extractEncryptionConfig(authRequest)

        assertTrue(result.isFailure, "Should fail when client_metadata is missing for encrypted mode")
        assertTrue(
            result.exceptionOrNull()?.message?.contains("client_metadata") == true,
            "Error should mention missing client_metadata"
        )
    }

    @Test
    fun extractEncryptionConfig_fails_whenJwksHasNoKeys() = runTest {
        val clientMetadata = ClientMetadata(
            jwks = ClientMetadata.Jwks(keys = emptyList()),
        )
        val authRequest = AuthorizationRequest(
            responseUri = "https://verifier.example/response",
            responseMode = OpenID4VPResponseMode.DIRECT_POST_JWT,
            clientId = "verifier-client",
            clientMetadata = clientMetadata,
        )

        val result = ResponseEncryptionHandler.extractEncryptionConfig(authRequest)

        assertTrue(result.isFailure, "Should fail when jwks has no keys")
    }

    @Test
    fun extractEncryptionConfig_prefersKeyWithUseEnc() = runTest {
        val signingKey = Json.parseToJsonElement("""
            {
                "kty": "EC",
                "crv": "P-256",
                "x": "y4ajD4aIXGiLGqiF81nN5HvBFvBEvrZcgFsp5VIJO30",
                "y": "jyrZRfxKz113LQNg2x5f7Nu4fwW5Ov5gCzhPaTZuTCg",
                "use": "sig",
                "alg": "ES256",
                "kid": "sig-key"
            }
        """.trimIndent()).jsonObject

        val encKey = Json.parseToJsonElement("""
            {
                "kty": "EC",
                "crv": "P-256",
                "x": "y4ajD4aIXGiLGqiF81nN5HvBFvBEvrZcgFsp5VIJO30",
                "y": "jyrZRfxKz113LQNg2x5f7Nu4fwW5Ov5gCzhPaTZuTCg",
                "use": "enc",
                "alg": "ECDH-ES",
                "kid": "enc-key"
            }
        """.trimIndent()).jsonObject

        val clientMetadata = ClientMetadata(
            jwks = ClientMetadata.Jwks(keys = listOf(signingKey, encKey)),
        )
        val authRequest = AuthorizationRequest(
            responseUri = "https://verifier.example/response",
            responseMode = OpenID4VPResponseMode.DIRECT_POST_JWT,
            clientId = "verifier-client",
            clientMetadata = clientMetadata,
        )

        val result = ResponseEncryptionHandler.extractEncryptionConfig(authRequest).getOrThrow()

        assertNotNull(result)
        assertEquals("enc-key", result.keyId)
    }

    @Test
    fun extractEncryptionConfig_rejectsJwkWithoutAlg() = runTest {
        val keyWithoutAlg = JsonObject(testEcPublicKeyJwk - "alg")
        val authRequest = AuthorizationRequest(
            responseUri = "https://verifier.example/response",
            responseMode = OpenID4VPResponseMode.DIRECT_POST_JWT,
            clientMetadata = ClientMetadata(jwks = ClientMetadata.Jwks(listOf(keyWithoutAlg))),
        )

        val result = ResponseEncryptionHandler.extractEncryptionConfig(authRequest)
        assertTrue(result.isFailure)
    }

    @Test
    fun extractEncryptionConfig_rejectsMissingOrDuplicateKid() = runTest {
        val missingKid = JsonObject(testEcPublicKeyJwk - "kid")
        val duplicateKid = JsonObject(testEcPublicKeyJwk + ("x" to JsonPrimitive("different-coordinate")))

        listOf(
            listOf(missingKid),
            listOf(testEcPublicKeyJwk, duplicateKid),
        ).forEach { keys ->
            val result = ResponseEncryptionHandler.extractEncryptionConfig(encryptedRequest(keys))
            assertTrue(result.isFailure)
        }
    }

    @Test
    fun extractEncryptionConfig_rejectsUnsupportedKeyAndEncAlgorithms() = runTest {
        val unsupportedKeys = listOf(
            JsonObject(testEcPublicKeyJwk + ("alg" to JsonPrimitive("ECDH-ES+A256KW"))),
            JsonObject(testEcPublicKeyJwk + ("crv" to JsonPrimitive("P-384"))),
            JsonObject(testEcPublicKeyJwk + ("use" to JsonPrimitive("sig"))),
        )
        unsupportedKeys.forEach { key ->
            assertTrue(ResponseEncryptionHandler.extractEncryptionConfig(encryptedRequest(listOf(key))).isFailure)
        }

        val unsupportedEnc = encryptedRequest(
            keys = listOf(testEcPublicKeyJwk),
            encValues = listOf("A128CBC-HS256"),
        )
        assertTrue(ResponseEncryptionHandler.extractEncryptionConfig(unsupportedEnc).isFailure)
    }

    @Test
    fun extractEncryptionConfig_selectsKeyDeterministically() = runTest {
        val keyZ = JsonObject(testEcPublicKeyJwk + ("kid" to JsonPrimitive("z-key")))
        val keyA = JsonObject(testEcPublicKeyJwk + ("kid" to JsonPrimitive("a-key")))

        val first = ResponseEncryptionHandler.extractEncryptionConfig(encryptedRequest(listOf(keyZ, keyA))).getOrThrow()
        val second = ResponseEncryptionHandler.extractEncryptionConfig(encryptedRequest(listOf(keyA, keyZ))).getOrThrow()

        assertEquals("a-key", first?.keyId)
        assertEquals(first?.keyId, second?.keyId)
        assertEquals(first?.verifierKeyThumbprint, second?.verifierKeyThumbprint)
    }

    @Test
    fun encryptResponse_usesNegotiatedProtectedHeader() = runTest {
        val config = ResponseEncryptionHandler.extractEncryptionConfig(
            encryptedRequest(listOf(testEcPublicKeyJwk), encValues = listOf("A256GCM"))
        ).getOrThrow()
        assertNotNull(config)
        val payload = buildJsonObject {
            put("vp_token", JsonPrimitive("credential-presentation"))
            put("state", JsonPrimitive("state-123"))
        }

        val jwe = ResponseEncryptionHandler.encryptResponse(payload, config)
        val protectedHeader = Json.parseToJsonElement(
            jwe.substringBefore('.').decodeFromBase64Url().decodeToString()
        ).jsonObject

        assertEquals(5, jwe.split('.').size)
        assertEquals("ECDH-ES", protectedHeader["alg"]?.jsonPrimitive?.content)
        assertEquals("A256GCM", protectedHeader["enc"]?.jsonPrimitive?.content)
        assertEquals("test-enc-key-1", protectedHeader["kid"]?.jsonPrimitive?.content)
    }

    private fun encryptedRequest(
        keys: List<JsonObject>,
        encValues: List<String>? = null,
    ) = AuthorizationRequest(
        responseUri = "https://verifier.example/response",
        responseMode = OpenID4VPResponseMode.DIRECT_POST_JWT,
        clientId = "verifier-client",
        clientMetadata = ClientMetadata(
            jwks = ClientMetadata.Jwks(keys),
            encryptedResponseEncValuesSupported = encValues,
        ),
    )
}
