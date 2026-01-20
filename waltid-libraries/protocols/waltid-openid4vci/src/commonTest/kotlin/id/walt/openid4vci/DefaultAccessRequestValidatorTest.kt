package id.walt.openid4vci

import id.walt.openid4vci.core.AccessRequestResult
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
        val request = (result as AccessRequestResult.Success).request
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
                "user_pin" to listOf("1234"),
                "scope" to listOf("openid profile"),
            ),
            session,
        )

        assertTrue(result.isSuccess())
        val request = (result as AccessRequestResult.Success).request
        assertTrue(request.grantTypes.contains(GrantType.PreAuthorizedCode.value))
        assertEquals("pre-auth-code", request.requestForm["pre-authorized_code"]?.firstOrNull())
        assertEquals("1234", request.requestForm["user_pin"]?.firstOrNull())
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
        val error = (result as AccessRequestResult.Failure).error
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
        val error = (result as AccessRequestResult.Failure).error
        assertEquals("unsupported_grant_type", error.error)
    }
}
