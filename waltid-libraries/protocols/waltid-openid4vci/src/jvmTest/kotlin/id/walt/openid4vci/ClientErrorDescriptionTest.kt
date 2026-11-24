package id.walt.openid4vci

import id.walt.openid4vci.core.buildOAuth2Provider
import id.walt.errors.ClientRequestException
import id.walt.openid4vci.errors.CredentialError
import id.walt.openid4vci.errors.CredentialErrorCodes
import id.walt.openid4vci.errors.CredentialProtocolException
import id.walt.openid4vci.errors.OAuthErrorCodes
import id.walt.openid4vci.errors.clientErrorDescription
import id.walt.openid4vci.requests.credential.CredentialRequestResult
import id.walt.openid4vci.tokens.access.AccessTokenAuthorization
import id.walt.openid4vci.tokens.access.AccessTokenAuthorizationScheme
import id.walt.openid4vci.tokens.access.AccessTokenVerifier
import id.walt.openid4vci.tokens.access.ProtectedResourceAccessTokenContext
import id.walt.openid4vci.tokens.jwt.access.JwtAccessTokenVerifier
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

/**
 * A failure inside the issuer must not reach the wallet's error_description, or an issuance event, with its
 * message: a session store, KMS or network error can name hosts and ports. A rejection of the wallet's own
 * request keeps its specific reason, but only when the validator marked it as such: a plain
 * IllegalArgumentException or SerializationException also arises from issuer configuration and stored data.
 */
class ClientErrorDescriptionTest {

    private val secretDetail = "kms://secret-host:8200 connection refused"

    @Test
    fun `an internal failure is described generically with an errorId`() {
        val description = clientErrorDescription(RuntimeException(secretDetail), "Access token could not be verified")

        assertFalse("secret-host" in description, description)
        assertContains(description, "Access token could not be verified (errorId=")
    }

    @Test
    fun `a rejection of the client's request keeps its reason`() {
        assertEquals("Access token expired", clientErrorDescription(ClientRequestException("Access token expired"), "x"))
        assertEquals(
            "Proof nonce is stale",
            clientErrorDescription(
                CredentialProtocolException(CredentialError(CredentialErrorCodes.INVALID_NONCE, "Proof nonce is stale")),
                "x",
            ),
        )
    }

    @Test
    fun `a decoding failure on server-owned data is described generically`() {
        val storedRecord = """{"statusListUri": "http://10.0.0.12:8080/internal/status", "index": "not-a-number"}"""
        val decodingFailure = assertFails { Json.decodeFromString<StoredStatusRecord>(storedRecord) }
        assertIs<SerializationException>(decodingFailure)
        assertContains(decodingFailure.message.orEmpty(), "not-a-number", message = "probe must carry the stored data")

        val description = clientErrorDescription(decodingFailure, "Credential issuance failed")

        assertFalse("not-a-number" in description || "10.0.0.12" in description, description)
        assertContains(description, "Credential issuance failed (errorId=")
    }

    @Test
    fun `a configuration failure raised as IllegalArgumentException is described generically`() {
        val configFailure = IllegalArgumentException("KMS key arn:aws:kms:eu-central-1:123456789012:key/issuer-signing not found")

        val description = clientErrorDescription(configFailure, "Access token could not be verified")

        assertFalse("arn:aws:kms" in description, description)
        assertContains(description, "errorId=")
    }

    @Test
    fun `an access token verifier failure is not returned to the wallet`() = runTest {
        val failure = createCredentialRequestWith { throw RuntimeException(secretDetail) }
        val response = buildOAuth2Provider(createTestConfig()).writeCredentialError(failure.error)

        assertEquals(OAuthErrorCodes.INVALID_TOKEN, failure.error.error)
        assertFalse("secret-host" in failure.error.description.orEmpty(), failure.error.description)
        assertContains(failure.error.description.orEmpty(), "errorId=")
        assertFalse("secret-host" in response.headers["WWW-Authenticate"].orEmpty())
    }

    @Test
    fun `an invalid access token keeps its reason`() = runTest {
        val failure = createCredentialRequestWith { throw ClientRequestException("Access token expired") }

        assertEquals(OAuthErrorCodes.INVALID_TOKEN, failure.error.error)
        assertEquals("Access token expired", failure.error.description)
    }

    @Test
    fun `a key resolution failure in the access token verifier is not returned to the wallet`() = runTest {
        val verifier = JwtAccessTokenVerifier.crypto2 {
            throw IllegalArgumentException("KMS key arn:aws:kms:eu-central-1:123456789012:key/issuer-signing not found")
        }
        val failure = createCredentialRequestWith(verifier)

        assertEquals(OAuthErrorCodes.INVALID_TOKEN, failure.error.error)
        assertFalse("arn:aws:kms" in failure.error.description.orEmpty(), failure.error.description)
        assertContains(failure.error.description.orEmpty(), "Access token could not be verified (errorId=")
    }

    @Test
    fun `a malformed access token keeps a client-facing reason`() = runTest {
        val verifier = JwtAccessTokenVerifier.crypto2 { error("key resolution must not be reached") }
        val failure = createCredentialRequestWith(verifier, token = "not-a-jwt")

        assertEquals("Malformed access token", failure.error.description)
    }

    @Serializable
    private data class StoredStatusRecord(val statusListUri: String, val index: Int)

    private suspend fun createCredentialRequestWith(verify: () -> JsonObject): CredentialRequestResult.OAuthFailure =
        createCredentialRequestWith(object : AccessTokenVerifier {
            override suspend fun verify(token: String, expectedIssuer: String?, expectedAudience: String?) = verify()
        })

    private suspend fun createCredentialRequestWith(
        verifier: AccessTokenVerifier,
        token: String = UNSIGNED_ACCESS_TOKEN,
    ): CredentialRequestResult.OAuthFailure {
        val provider = buildOAuth2Provider(createTestConfig(accessTokenVerifier = verifier))
        return assertIs<CredentialRequestResult.OAuthFailure>(
            provider.createCredentialRequest(
                parameters = emptyMap(),
                accessTokenContext = ProtectedResourceAccessTokenContext(
                    authorization = AccessTokenAuthorization(AccessTokenAuthorizationScheme.BEARER, token),
                    expectedIssuer = "https://issuer.example",
                    targetUri = "https://issuer.example/credential",
                ),
            ),
        )
    }

    private companion object {
        // {"alg":"ES256","kid":"issuer-key"}.{"sub":"wallet"}.<zero signature>: decodes, never verifies.
        val UNSIGNED_ACCESS_TOKEN = listOf(
            """{"alg":"ES256","kid":"issuer-key"}""", """{"sub":"wallet"}""",
        ).joinToString(".") { Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(it.encodeToByteArray()) } +
            "." + Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(ByteArray(64))
    }
}
