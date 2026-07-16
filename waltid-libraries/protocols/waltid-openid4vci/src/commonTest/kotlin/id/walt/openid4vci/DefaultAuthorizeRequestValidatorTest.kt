package id.walt.openid4vci

import id.walt.openid4vci.errors.OAuthError
import id.walt.openid4vci.requests.authorization.AuthorizationRequestResult
import id.walt.openid4vci.validation.DefaultAuthorizationRequestValidator
import kotlin.test.assertEquals
import kotlin.test.Test
import kotlin.test.assertTrue

class DefaultAuthorizeRequestValidatorTest {

    private val validator = DefaultAuthorizationRequestValidator()

    @Test
    fun `validate succeeds for response_type code`() {
        val result = validator.validate(
            mapOf(
                "client_id" to listOf("client-123"),
                "response_type" to listOf(ResponseType.CODE.value),
                "redirect_uri" to listOf("https://openid4vci.walt.id/callback"),
                "scope" to listOf("openid offline"),
                "state" to listOf("xyz"),
                "issuer_state" to listOf("issuer-state-123"),
            ),
        )

        assertTrue(result.isSuccess())
        val request = (result as AuthorizationRequestResult.Success).request
        assertEquals("client-123", request.client.id)
        assertTrue(request.responseTypes.contains("code"))
        assertEquals("https://openid4vci.walt.id/callback", request.redirectUri)
        assertTrue(request.requestedScopes.contains("openid"))
        assertEquals("xyz", request.state)
        assertEquals("issuer-state-123", request.issuerState)
    }

    @Test
    fun `validate parses authorization_details`() {
        val result = validator.validate(
            mapOf(
                "client_id" to listOf("client-123"),
                "response_type" to listOf(ResponseType.CODE.value),
                "authorization_details" to listOf(
                    """[{"type":"openid_credential","credential_configuration_id":"w3c_jwt_c"}]"""
                ),
            ),
        )

        assertTrue(result.isSuccess())
        val request = (result as AuthorizationRequestResult.Success).request
        assertEquals(1, request.authorizationDetails.size)
        assertEquals("openid_credential", request.authorizationDetails.single().type)
        assertEquals("w3c_jwt_c", request.authorizationDetails.single().credentialConfigurationId)
    }

    @Test
    fun `validate accepts unknown authorization_details fields`() {
        val result = validator.validate(
            mapOf(
                "client_id" to listOf("client-123"),
                "response_type" to listOf(ResponseType.CODE.value),
                "authorization_details" to listOf(
                    """[{"type":"openid_credential","credential_configuration_id":"w3c_jwt_c","unexpected":"value"}]"""
                ),
            ),
        )

        assertTrue(result.isSuccess())
        val request = (result as AuthorizationRequestResult.Success).request
        assertEquals("w3c_jwt_c", request.authorizationDetails.single().credentialConfigurationId)
    }

    @Test
    fun `validate rejects malformed authorization_details JSON`() {
        val result = validator.validate(
            mapOf(
                "client_id" to listOf("client-123"),
                "response_type" to listOf(ResponseType.CODE.value),
                "authorization_details" to listOf("not-json"),
            ),
        )

        assertTrue(!result.isSuccess())
        val error = (result as AuthorizationRequestResult.Failure).error
        assertEquals("invalid_request", error.error)
        assertTrue(
            error.description?.contains("Invalid authorization_details") == true,
            "Expected invalid authorization_details error description"
        )
    }

    @Test
    fun `validate rejects duplicate authorization_details parameters`() {
        val result = validator.validate(
            mapOf(
                "client_id" to listOf("client-123"),
                "response_type" to listOf(ResponseType.CODE.value),
                "authorization_details" to listOf(
                    """[{"type":"openid_credential","credential_configuration_id":"w3c_jwt_c"}]""",
                    """[{"type":"openid_credential","credential_configuration_id":"w3c_jwt_c"}]""",
                ),
            ),
        )

        assertTrue(!result.isSuccess())
        val error = (result as AuthorizationRequestResult.Failure).error
        assertEquals("invalid_request", error.error)
        assertTrue(
            error.description?.contains("Multiple values for authorization_details") == true,
            "Expected duplicate authorization_details to be rejected"
        )
    }

    @Test
    fun `validate rejects missing response_type`() {
        val result = validator.validate(
            mapOf(
                "client_id" to listOf("client-123"),
            ),
        )

        assertTrue(!result.isSuccess())
        val error = (result as AuthorizationRequestResult.Failure).error
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
        val error = (result as AuthorizationRequestResult.Failure).error
        assertEquals("unsupported_response_type", error.error)
    }

    @Test
    fun `validate rejects hybrid response_type`() {
        val result = validator.validate(
            mapOf(
                "client_id" to listOf("client-123"),
                "response_type" to listOf("code id_token"),
            ),
        )

        assertTrue(!result.isSuccess())
        val error = (result as AuthorizationRequestResult.Failure).error
        assertEquals("unsupported_response_type", error.error)
    }

    @Test
    fun `validate rejects duplicate client_id parameters`() {
        val result = validator.validate(
            mapOf(
                "client_id" to listOf("client-123", "client-456"),
                "response_type" to listOf(ResponseType.CODE.value),
            ),
        )

        assertTrue(!result.isSuccess())
        val error = (result as AuthorizationRequestResult.Failure).error
        assertEquals("invalid_request", error.error)
        assertTrue(error.description?.contains("Multiple values for client_id") == true)
    }

    @Test
    fun `validate rejects duplicate redirect_uri parameters`() {
        val result = validator.validate(
            mapOf(
                "client_id" to listOf("client-123"),
                "response_type" to listOf(ResponseType.CODE.value),
                "redirect_uri" to listOf("https://wallet.example/callback", "https://attacker.example/callback"),
            ),
        )

        assertTrue(!result.isSuccess())
        val error = (result as AuthorizationRequestResult.Failure).error
        assertEquals("invalid_request", error.error)
        assertTrue(error.description?.contains("Multiple values for redirect_uri") == true)
    }

    @Test
    fun `validate rejects duplicate forwarded parameters`() {
        val result = validator.validate(
            mapOf(
                "client_id" to listOf("client-123"),
                "response_type" to listOf(ResponseType.CODE.value),
                "scope" to listOf("openid", "credential"),
            ),
        )

        assertTrue(!result.isSuccess())
        val error = (result as AuthorizationRequestResult.Failure).error
        assertEquals("invalid_request", error.error)
        assertTrue(error.description?.contains("Multiple values for scope") == true)
    }

    @Test
    fun `validate runs issuer_state hook`() {
        val hookValidator = DefaultAuthorizationRequestValidator { issuerState, _ ->
            if (issuerState == "blocked") {
                OAuthError("invalid_request", "issuer_state rejected")
            } else {
                null
            }
        }

        val rejected = hookValidator.validate(
            mapOf(
                "client_id" to listOf("client-123"),
                "response_type" to listOf(ResponseType.CODE.value),
                "issuer_state" to listOf("blocked"),
            ),
        )
        assertTrue(rejected is AuthorizationRequestResult.Failure)
        val error = (rejected).error
        assertEquals("invalid_request", error.error)

        val accepted = hookValidator.validate(
            mapOf(
                "client_id" to listOf("client-123"),
                "response_type" to listOf(ResponseType.CODE.value),
                "issuer_state" to listOf("ok"),
            ),
        )
        assertTrue(accepted is AuthorizationRequestResult.Success)
    }
}
