package id.walt.openid4vci

import id.walt.openid4vci.core.AccessRequestResult
import id.walt.openid4vci.validation.DefaultAccessRequestValidator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

class DefaultAccessRequestValidatorTest {

    private val validator = DefaultAccessRequestValidator()

    @OptIn(ExperimentalTime::class)
    @Test
    fun `validate accepts authorization code grant`() {
        val session = DefaultSession()
        val result = validator.validate(
            mapOf(
                "grant_type" to GRANT_TYPE_AUTHORIZATION_CODE,
                "client_id" to "client-123",
                "code" to "auth-code",
                "redirect_uri" to "https://openid4vci.walt.id/callback",
            ),
            session,
        )

        assertTrue(result.isSuccess())
        val request = (result as AccessRequestResult.Success).request
        assertTrue(request.getGrantTypes().contains(GRANT_TYPE_AUTHORIZATION_CODE))
        assertEquals("auth-code", request.getRequestForm().getFirst("code"))
        assertEquals("client-123", request.getClient().id)
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun `validate accepts pre-authorized code grant without client id`() {
        val session = DefaultSession()
        val result = validator.validate(
            mapOf(
                "grant_type" to GRANT_TYPE_PRE_AUTHORIZED_CODE,
                "pre-authorized_code" to "pre-auth-code",
                "user_pin" to "1234",
                "scope" to "openid profile",
            ),
            session,
        )

        assertTrue(result.isSuccess())
        val request = (result as AccessRequestResult.Success).request
        assertTrue(request.getGrantTypes().contains(GRANT_TYPE_PRE_AUTHORIZED_CODE))
        assertEquals("pre-auth-code", request.getRequestForm().getFirst("pre-authorized_code"))
        assertEquals("1234", request.getRequestForm().getFirst("user_pin"))
        assertTrue(request.getRequestedScopes().contains("openid"))
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun `validate rejects pre-authorized code grant missing code`() {
        val result = validator.validate(
            mapOf(
                "grant_type" to GRANT_TYPE_PRE_AUTHORIZED_CODE,
            ),
            DefaultSession(),
        )

        assertTrue(!result.isSuccess())
        val error = (result as AccessRequestResult.Failure).error
        assertEquals("invalid_request", error.error)
    }

    @OptIn(ExperimentalTime::class)
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
