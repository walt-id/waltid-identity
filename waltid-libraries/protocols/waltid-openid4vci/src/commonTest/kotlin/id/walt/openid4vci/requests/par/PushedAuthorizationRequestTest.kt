package id.walt.openid4vci.requests.par

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PushedAuthorizationRequestTest {

    @Test
    fun `should create PAR with required client_id`() {
        val par = PushedAuthorizationRequest(clientId = "test-client")
        assertEquals("test-client", par.clientId)
    }

    @Test
    fun `should fail when client_id is blank`() {
        assertFailsWith<IllegalArgumentException> {
            PushedAuthorizationRequest(clientId = "")
        }
    }

    @Test
    fun `should create PAR with all standard parameters`() {
        val par = PushedAuthorizationRequest(
            clientId = "test-client",
            responseType = "code",
            redirectUri = "https://example.com/callback",
            scope = "openid profile",
            state = "state123",
            codeChallenge = "challenge123",
            codeChallengeMethod = "S256",
            authorizationDetails = """[{"type":"openid_credential"}]""",
            walletIssuer = "https://wallet.example.com",
            userHint = "user@example.com",
            issuerState = "issuer-state-123",
        )

        assertEquals("test-client", par.clientId)
        assertEquals("code", par.responseType)
        assertEquals("https://example.com/callback", par.redirectUri)
        assertEquals("openid profile", par.scope)
        assertEquals("state123", par.state)
        assertEquals("challenge123", par.codeChallenge)
        assertEquals("S256", par.codeChallengeMethod)
        assertNotNull(par.authorizationDetails)
        assertEquals("https://wallet.example.com", par.walletIssuer)
        assertEquals("user@example.com", par.userHint)
        assertEquals("issuer-state-123", par.issuerState)
    }

    @Test
    fun `should parse PAR from form parameters`() {
        val params = mapOf(
            "client_id" to listOf("test-client"),
            "response_type" to listOf("code"),
            "redirect_uri" to listOf("https://example.com/callback"),
            "scope" to listOf("openid"),
            "state" to listOf("abc123"),
            "code_challenge" to listOf("xyz789"),
            "code_challenge_method" to listOf("S256"),
        )

        val par = PushedAuthorizationRequest.fromParameters(params)

        assertEquals("test-client", par.clientId)
        assertEquals("code", par.responseType)
        assertEquals("https://example.com/callback", par.redirectUri)
        assertEquals("openid", par.scope)
        assertEquals("abc123", par.state)
        assertEquals("xyz789", par.codeChallenge)
        assertEquals("S256", par.codeChallengeMethod)
    }

    @Test
    fun `should fail to parse PAR without client_id`() {
        val params = mapOf(
            "response_type" to listOf("code"),
            "scope" to listOf("openid"),
        )

        assertFailsWith<IllegalArgumentException> {
            PushedAuthorizationRequest.fromParameters(params)
        }
    }

    @Test
    fun `should capture additional parameters`() {
        val params = mapOf(
            "client_id" to listOf("test-client"),
            "custom_param" to listOf("custom_value"),
            "another_param" to listOf("another_value"),
        )

        val par = PushedAuthorizationRequest.fromParameters(params)

        assertEquals("test-client", par.clientId)
        assertEquals(2, par.additionalParameters.size)
        assertEquals("custom_value", par.additionalParameters["custom_param"])
        assertEquals("another_value", par.additionalParameters["another_param"])
    }

    @Test
    fun `should convert PAR to authorization parameters`() {
        val par = PushedAuthorizationRequest(
            clientId = "test-client",
            responseType = "code",
            redirectUri = "https://example.com/callback",
            scope = "openid",
            state = "state123",
            additionalParameters = mapOf("custom" to "value"),
        )

        val authParams = par.toAuthorizationParameters()

        assertEquals(listOf("test-client"), authParams["client_id"])
        assertEquals(listOf("code"), authParams["response_type"])
        assertEquals(listOf("https://example.com/callback"), authParams["redirect_uri"])
        assertEquals(listOf("openid"), authParams["scope"])
        assertEquals(listOf("state123"), authParams["state"])
        assertEquals(listOf("value"), authParams["custom"])
    }

    @Test
    fun `should only include non-null parameters in authorization parameters`() {
        val par = PushedAuthorizationRequest(
            clientId = "test-client",
            responseType = "code",
        )

        val authParams = par.toAuthorizationParameters()

        assertTrue(authParams.containsKey("client_id"))
        assertTrue(authParams.containsKey("response_type"))
        assertEquals(2, authParams.size) // Only client_id and response_type
    }
}
