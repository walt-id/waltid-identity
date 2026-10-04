package id.walt.openid4vci

import id.walt.openid4vci.core.buildOAuth2Provider
import id.walt.openid4vci.errors.CredentialError
import id.walt.openid4vci.errors.CredentialErrorCodes
import id.walt.openid4vci.errors.OAuthError
import id.walt.openid4vci.errors.OAuthErrorCodes
import id.walt.openid4vci.requests.credential.DefaultCredentialRequest
import id.walt.openid4vci.responses.credential.CredentialResponse
import id.walt.openid4vci.responses.credential.IssuedCredential
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CredentialResponseCacheControlTest {
    private val provider = buildOAuth2Provider(createTestConfig())

    @Test
    fun plaintextCredentialsProhibitCaching() = runTest {
        val request = DefaultCredentialRequest(
            client = DefaultClient("wallet", emptyList(), emptySet(), emptySet()),
            credentialIdentifier = null,
            credentialConfigurationId = "credential",
            proofs = null,
            credentialResponseEncryption = null,
        )
        val response = provider.writeCredentialResponse(
            request,
            CredentialResponse(credentials = listOf(IssuedCredential(JsonPrimitive("synthetic-credential")))),
        )
        assertEquals(200, response.status)
        assertEquals("no-store", response.headers["Cache-Control"])
        assertTrue(response.payload.containsKey("credentials"))
    }

    @Test
    fun protocolErrorsProhibitCaching() {
        val response = provider.writeCredentialError(CredentialError(CredentialErrorCodes.INVALID_PROOF, "Invalid proof"))
        assertEquals(400, response.status)
        assertEquals("no-store", response.headers["Cache-Control"])
        assertEquals(JsonPrimitive(CredentialErrorCodes.INVALID_PROOF), response.payload["error"])
    }

    @Test
    fun authorizationErrorsProhibitCachingAndPreserveChallenges() {
        for (code in listOf(OAuthErrorCodes.INVALID_TOKEN, OAuthErrorCodes.INVALID_DPOP_PROOF)) {
            val response = provider.writeCredentialError(OAuthError(code, "Invalid proof or token"))
            assertEquals(401, response.status)
            assertEquals("no-store", response.headers["Cache-Control"])
            assertTrue(response.headers["WWW-Authenticate"]?.contains(code) == true)
            assertEquals(JsonPrimitive(code), response.payload["error"])
        }
    }
}
