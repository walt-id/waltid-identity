package id.walt.openid4vci

import id.walt.openid4vci.core.AccessRequestResult
import id.walt.openid4vci.validation.DefaultAccessRequestValidator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultAccessRequestValidatorTest {

    private val validator = DefaultAccessRequestValidator()

    @Test
    fun `validate accepts authorization code grant`() {
        val session = DefaultSession()
        val result = validator.validate(
            mapOf(
                "grant_type" to GrantType.AuthorizationCode.value,
                "client_id" to "client-123",
                "code" to "auth-code",
                "redirect_uri" to "https://openid4vci.walt.id/callback",
            ),
            session,
        )

        assertTrue(result.isSuccess())
        val request = (result as AccessRequestResult.Success).request
        assertTrue(request.getGrantTypes().contains(GrantType.AuthorizationCode.value))
        assertEquals("auth-code", request.getRequestForm().getFirst("code"))
        assertEquals("client-123", request.getClient().id)
    }

    @Test
    fun `validate accepts pre-authorized code grant without client id`() {
        val session = DefaultSession()
        val result = validator.validate(
            mapOf(
                "grant_type" to GrantType.PreAuthorizedCode.value,
                "pre-authorized_code" to "pre-auth-code",
                "user_pin" to "1234",
                "scope" to "openid profile",
            ),
            session,
        )

        assertTrue(result.isSuccess())
        val request = (result as AccessRequestResult.Success).request
        assertTrue(request.getGrantTypes().contains(GrantType.PreAuthorizedCode.value))
        assertEquals("pre-auth-code", request.getRequestForm().getFirst("pre-authorized_code"))
        assertEquals("1234", request.getRequestForm().getFirst("user_pin"))
        assertTrue(request.getRequestedScopes().contains("openid"))
    }

    @Test
    fun `validate rejects pre-authorized code grant missing code`() {
        val result = validator.validate(
            mapOf(
                "grant_type" to GrantType.PreAuthorizedCode.value,
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
                "grant_type" to "client_credentials",
            ),
            DefaultSession(),
        )

        assertTrue(!result.isSuccess())
        val error = (result as AccessRequestResult.Failure).error
        assertEquals("unsupported_grant_type", error.error)
    }
}
