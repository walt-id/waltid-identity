package id.walt.openid4vci

import id.walt.openid4vci.requests.authorization.AuthorizeRequestResult
import id.walt.openid4vci.validation.DefaultAuthorizationRequestValidator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultAuthorizeRequestValidatorTest {

    private val validator = DefaultAuthorizationRequestValidator()

    @Test
    fun `validate succeeds for response_type code`() {
        val result = validator.validate(
            mapOf(
                "client_id" to listOf("client-123"),
                "response_type" to listOf("code"),
                "redirect_uri" to listOf("https://openid4vci.walt.id/callback"),
                "scope" to listOf("openid offline"),
                "state" to listOf("xyz"),
            ),
        )

        assertTrue(result.isSuccess())
        val request = (result as AuthorizeRequestResult.Success).request
        assertEquals("client-123", request.client.id)
        assertTrue(request.responseTypes.contains("code"))
        assertEquals("https://openid4vci.walt.id/callback", request.redirectUri)
        assertTrue(request.requestedScopes.contains("openid"))
        assertEquals("xyz", request.state)
    }

    @Test
    fun `validate rejects missing response_type`() {
        val result = validator.validate(
            mapOf(
                "client_id" to listOf("client-123"),
            ),
        )

        assertTrue(!result.isSuccess())
        val error = (result as AuthorizeRequestResult.Failure).error
        assertEquals("invalid_request", error.error)
    }

    @Test
    fun `validate rejects unsupported response_type`() {
        val result = validator.validate(
            mapOf(
                "client_id" to listOf("client-123"),
                "response_type" to listOf("token"),
            ),
        )

        assertTrue(!result.isSuccess())
        val error = (result as AuthorizeRequestResult.Failure).error
        assertEquals("unsupported_response_type", error.error)
    }
}
