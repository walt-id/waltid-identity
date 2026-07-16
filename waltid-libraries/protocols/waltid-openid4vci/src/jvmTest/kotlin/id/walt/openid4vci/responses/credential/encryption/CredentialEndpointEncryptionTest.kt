package id.walt.openid4vci.responses.credential.encryption

import id.walt.crypto.keys.JwkKeyMeta
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.JweUtils
import id.walt.openid4vci.DefaultSession
import id.walt.openid4vci.core.buildOAuth2Provider
import id.walt.openid4vci.createTestConfig
import id.walt.openid4vci.errors.CredentialErrorCodes
import id.walt.openid4vci.requests.credential.CredentialRequestResult
import id.walt.openid4vci.requests.credential.encryption.CredentialEncryptionProfile
import id.walt.openid4vci.requests.credential.encryption.JweCredentialRequestDecryptor
import id.walt.openid4vci.responses.credential.CredentialResponse
import id.walt.openid4vci.responses.credential.CredentialResponseBody
import id.walt.openid4vci.responses.credential.IssuedCredential
import id.walt.openid4vci.responses.credential.toJsonObject
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CredentialEndpointEncryptionTest {

    @Test
    fun `plaintext credential request rejects credential response encryption`() = runBlocking {
        val walletKey = JWKKey.generate(KeyType.secp256r1, JwkKeyMeta("wallet-key"))
        val provider = buildOAuth2Provider(createTestConfig())

        val result = provider.createCredentialRequest(
            parameters = mapOf(
                "credential_configuration_id" to listOf("test-credential"),
                "credential_response_encryption" to listOf(responseEncryptionRequest(walletKey).toString()),
            ),
            session = DefaultSession(subject = "subject"),
        )

        assertTrue(result is CredentialRequestResult.Failure)
        assertEquals("invalid_request", result.error.error)
        assertEquals(
            "credential_response_encryption requires an encrypted Credential Request",
            result.error.description,
        )
    }

    @Test
    fun `encrypted credential request receives encrypted credential response`() = runBlocking {
        val issuerKey = JWKKey.generate(KeyType.secp256r1, JwkKeyMeta("issuer-key"))
        val walletKey = JWKKey.generate(KeyType.secp256r1, JwkKeyMeta("wallet-key"))
        val provider = buildOAuth2Provider(
            createTestConfig(
                credentialRequestDecryptor = JweCredentialRequestDecryptor(issuerKey.exportJWK()),
            )
        )

        val credentialRequestJwe = encryptCredentialRequest(
            payload = buildJsonObject {
                put("credential_configuration_id", "test-credential")
                put("credential_response_encryption", responseEncryptionRequest(walletKey))
            },
            issuerKey = issuerKey,
        )

        val requestResult = provider.createCredentialRequest(
            encryptedCredentialRequest = credentialRequestJwe,
            session = DefaultSession(subject = "subject"),
        )

        assertTrue(requestResult is CredentialRequestResult.Success)
        assertNotNull(requestResult.request.credentialResponseEncryption)

        val response = CredentialResponse(
            credentials = listOf(
                IssuedCredential(JsonPrimitive("issued-credential")),
            ),
        )
        val http = provider.writeCredentialResponse(requestResult.request, response)

        assertEquals(200, http.status)
        assertTrue(http.body is CredentialResponseBody.EncryptedJwt)
        assertEquals(CredentialEncryptionProfile.MEDIA_TYPE_JWT, http.contentType)

        val encryptedJwt = assertNotNull(http.encryptedJwt)
        val parts = JweUtils.parseJWE(encryptedJwt, walletKey.exportJWK())
        assertEquals(CredentialEncryptionProfile.ALG_ECDH_ES, parts.header["alg"]?.jsonPrimitive?.contentOrNull)
        assertEquals(CredentialEncryptionProfile.ENC_A128GCM, parts.header["enc"]?.jsonPrimitive?.contentOrNull)
        assertEquals(walletKey.getKeyId(), parts.header["kid"]?.jsonPrimitive?.contentOrNull)
        assertEquals(response.toJsonObject(), parts.payload)
    }

    @Test
    fun `encrypted credential request fails when request decryptor is not configured`() = runBlocking {
        val provider = buildOAuth2Provider(createTestConfig())

        val requestResult = provider.createCredentialRequest(
            encryptedCredentialRequest = "encrypted-request",
            session = DefaultSession(subject = "subject"),
        )

        assertTrue(requestResult is CredentialRequestResult.Failure)
        assertEquals(CredentialErrorCodes.ENCRYPTION_NOT_SUPPORTED, requestResult.error.error)
        assertEquals("credential request encryption is not supported", requestResult.error.description)
    }

    @Test
    fun `encrypted credential request without response encryption receives json response`() = runBlocking {
        val issuerKey = JWKKey.generate(KeyType.secp256r1, JwkKeyMeta("issuer-key"))
        val provider = buildOAuth2Provider(
            createTestConfig(
                credentialRequestDecryptor = JweCredentialRequestDecryptor(issuerKey.exportJWK()),
            )
        )

        val credentialRequestJwe = encryptCredentialRequest(
            payload = buildJsonObject {
                put("credential_configuration_id", "test-credential")
            },
            issuerKey = issuerKey,
        )

        val requestResult = provider.createCredentialRequest(
            encryptedCredentialRequest = credentialRequestJwe,
            session = DefaultSession(subject = "subject"),
        )

        assertTrue(requestResult is CredentialRequestResult.Success)

        val response = CredentialResponse(
            credentials = listOf(
                IssuedCredential(JsonPrimitive("issued-credential")),
            ),
        )
        val http = provider.writeCredentialResponse(requestResult.request, response)

        assertEquals(200, http.status)
        assertTrue(http.body is CredentialResponseBody.Json)
        assertEquals(response.toJsonObject(), JsonObject(http.payload))
    }

    private suspend fun encryptCredentialRequest(
        payload: JsonObject,
        issuerKey: JWKKey,
    ): String {
        val issuerPublicJwk = encryptionPublicJwk(issuerKey)
        return JweUtils.toJWE(
            payload = payload,
            jwk = issuerPublicJwk.toString(),
            alg = CredentialEncryptionProfile.ALG_ECDH_ES,
            enc = CredentialEncryptionProfile.ENC_A128GCM,
            headerParams = mapOf("kid" to JsonPrimitive(issuerKey.getKeyId())),
        )
    }

    private suspend fun responseEncryptionRequest(walletKey: JWKKey): JsonObject =
        buildJsonObject {
            put("jwk", encryptionPublicJwk(walletKey))
            put("enc", CredentialEncryptionProfile.ENC_A128GCM)
        }

    private suspend fun encryptionPublicJwk(key: Key): JsonObject =
        JsonObject(
            key.getPublicKey().exportJWKObject().toMutableMap().apply {
                put("kid", JsonPrimitive(key.getKeyId()))
                put("use", JsonPrimitive(CredentialEncryptionProfile.KEY_USE_ENC))
                put("alg", JsonPrimitive(CredentialEncryptionProfile.ALG_ECDH_ES))
            }
        )
}
