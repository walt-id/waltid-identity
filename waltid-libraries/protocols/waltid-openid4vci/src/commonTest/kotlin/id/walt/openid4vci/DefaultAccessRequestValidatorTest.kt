package id.walt.openid4vci

import id.walt.openid4vci.requests.token.AccessTokenRequestResult
import id.walt.openid4vci.validation.DefaultAccessTokenRequestValidator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultAccessRequestValidatorTest {

    private val validator = DefaultAccessTokenRequestValidator()

    @Test
    fun `validate accepts authorization code grant`() {
        val session = DefaultSession()
        val result = validator.validate(
            mapOf(
                "grant_type" to listOf(GrantType.AuthorizationCode.value),
                "client_id" to listOf("client-123"),
                "code" to listOf("auth-code"),
                "redirect_uri" to listOf("https://openid4vci.walt.id/callback"),
            ),
            session,
        )

        assertTrue(result.isSuccess())
        val request = (result as AccessTokenRequestResult.Success).request
        assertTrue(request.grantTypes.contains(GrantType.AuthorizationCode.value))
        assertEquals("auth-code", request.requestForm["code"]?.firstOrNull())
        assertEquals("client-123", request.client.id)
    }

    @Test
    fun `validate accepts pre-authorized code grant without client id`() {
        val session = DefaultSession()
        val result = validator.validate(
            mapOf(
                "grant_type" to listOf(GrantType.PreAuthorizedCode.value),
                "pre-authorized_code" to listOf("pre-auth-code"),
                "tx_code" to listOf("1234"),
                "scope" to listOf("openid profile"),
            ),
            session,
        )

        assertTrue(result.isSuccess())
        val request = (result as AccessTokenRequestResult.Success).request
        assertTrue(request.grantTypes.contains(GrantType.PreAuthorizedCode.value))
        assertEquals("pre-auth-code", request.requestForm["pre-authorized_code"]?.firstOrNull())
        assertEquals("1234", request.requestForm["tx_code"]?.firstOrNull())
        assertTrue(request.requestedScopes.contains("openid"))
    }

    @Test
    fun `validate rejects pre-authorized code grant missing code`() {
        val result = validator.validate(
            mapOf(
                "grant_type" to listOf(GrantType.PreAuthorizedCode.value),
            ),
            DefaultSession(),
        )

        assertTrue(!result.isSuccess())
        val error = (result as AccessTokenRequestResult.Failure).error
        assertEquals("invalid_request", error.error)
    }

    @Test
    fun `validate accepts refresh token grant`() {
        val result = validator.validate(
            mapOf(
                "grant_type" to listOf(GrantType.RefreshToken.value),
                "client_id" to listOf("client-123"),
                "refresh_token" to listOf("refresh-token"),
                "scope" to listOf("openid profile"),
            ),
            DefaultSession(),
        )

        assertTrue(result.isSuccess())
        val request = (result as AccessTokenRequestResult.Success).request
        assertTrue(request.grantTypes.contains(GrantType.RefreshToken.value))
        assertEquals("client-123", request.client.id)
        assertEquals("refresh-token", request.requestForm["refresh_token"]?.firstOrNull())
        assertTrue(request.requestedScopes.contains("openid"))
    }

    @Test
    fun `validate rejects refresh token grant missing refresh token`() {
        val result = validator.validate(
            mapOf(
                "grant_type" to listOf(GrantType.RefreshToken.value),
                "client_id" to listOf("client-123"),
            ),
            DefaultSession(),
        )

        assertTrue(!result.isSuccess())
        val error = (result as AccessTokenRequestResult.Failure).error
        assertEquals("invalid_request", error.error)
    }

    @Test
    fun `validate rejects unsupported grant type`() {
        val result = validator.validate(
            mapOf(
                "grant_type" to listOf("client_credentials"),
            ),
            DefaultSession(),
        )

        assertTrue(!result.isSuccess())
        val error = (result as AccessTokenRequestResult.Failure).error
        assertEquals("unsupported_grant_type", error.error)
    }
}
