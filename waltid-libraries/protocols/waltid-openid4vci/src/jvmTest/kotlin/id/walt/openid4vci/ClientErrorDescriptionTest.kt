package id.walt.openid4vci

import id.walt.openid4vci.core.buildOAuth2Provider
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
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

/**
 * A failure inside the issuer must not reach the wallet's error_description, or an issuance event, with its
 * message: a session store, KMS or network error can name hosts and ports. A rejection of the wallet's own
 * request keeps its specific reason.
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
        assertEquals("Access token expired", clientErrorDescription(IllegalArgumentException("Access token expired"), "x"))
        assertEquals("Unexpected JSON token", clientErrorDescription(SerializationException("Unexpected JSON token"), "x"))
        assertEquals(
            "Proof nonce is stale",
            clientErrorDescription(
                CredentialProtocolException(CredentialError(CredentialErrorCodes.INVALID_NONCE, "Proof nonce is stale")),
                "x",
            ),
        )
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
        val failure = createCredentialRequestWith { throw IllegalArgumentException("Access token expired") }

        assertEquals(OAuthErrorCodes.INVALID_TOKEN, failure.error.error)
        assertEquals("Access token expired", failure.error.description)
    }

    private suspend fun createCredentialRequestWith(verify: () -> JsonObject): CredentialRequestResult.OAuthFailure {
        val provider = buildOAuth2Provider(
            createTestConfig(
                accessTokenVerifier = object : AccessTokenVerifier {
                    override suspend fun verify(token: String, expectedIssuer: String?, expectedAudience: String?) = verify()
                },
            ),
        )
        return assertIs<CredentialRequestResult.OAuthFailure>(
            provider.createCredentialRequest(
                parameters = emptyMap(),
                accessTokenContext = ProtectedResourceAccessTokenContext(
                    authorization = AccessTokenAuthorization(AccessTokenAuthorizationScheme.BEARER, "access-token"),
                    expectedIssuer = "https://issuer.example",
                    targetUri = "https://issuer.example/credential",
                ),
            ),
        )
    }
}
