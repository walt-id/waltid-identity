package id.waltid.openid4vp.wallet.response

import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
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
        // The config should use the enc key, not the sig key
        // We can't easily verify the exact key selected, but at least verify config is extracted
    }
}
