package id.waltid.openid4vci.wallet.authorization

import id.waltid.openid4vci.wallet.oauth.ClientConfiguration
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AuthorizationRequestBuilderTest {

    private val clientConfig = ClientConfiguration(
        clientId = "test-client",
        redirectUris = listOf("https://wallet.example.com/callback")
    )
    private val builder = AuthorizationRequestBuilder(clientConfig)
    private val endpoint = "https://auth.example.com/authorize"

    @Test
    fun testBuildAuthorizationRequestBasic() {
        val request = builder.buildAuthorizationRequest(
            authorizationEndpoint = endpoint,
            credentialConfigurationId = "test_config"
        )

        val url = Url(request.url)
        assertEquals("auth.example.com", url.host)
        assertEquals("/authorize", url.encodedPath)

        val params = url.parameters
        assertEquals("code", params["response_type"])
        assertEquals("test-client", params["client_id"])
        assertEquals("https://wallet.example.com/callback", params["redirect_uri"])
        assertNotNull(params["state"])
        val authorizationDetail = authorizationDetail(params)
        assertEquals("openid_credential", authorizationDetail["type"]?.jsonPrimitive?.content)
        assertEquals("test_config", authorizationDetail["credential_configuration_id"]?.jsonPrimitive?.content)
    }

    @Test
    fun testBuildAuthorizationRequestWithPKCE() {
        val request = builder.buildAuthorizationRequest(
            authorizationEndpoint = endpoint,
            credentialConfigurationId = "test_config",
            usePKCE = true
        )

        val pkceData = assertNotNull(request.pkceData)
        val url = Url(request.url)
        val params = url.parameters
        assertEquals(pkceData.codeChallenge, params["code_challenge"])
        assertEquals("S256", params["code_challenge_method"])
    }

    @Test
    fun testBuildAuthorizationRequestWithIssuerStateAndScope() {
        val issuerState = "issuer-state-123"
        val scope = "openid profile"

        val request = builder.buildAuthorizationRequest(
            authorizationEndpoint = endpoint,
            credentialConfigurationId = "test_config",
            issuerState = issuerState,
            scope = scope
        )

        val url = Url(request.url)
        assertEquals(issuerState, url.parameters["issuer_state"])
        assertEquals(scope, url.parameters["scope"])
    }

    @Test
    fun testBuildPushedAuthorizationRequest() {
        val (params, pkce) = builder.buildPushedAuthorizationRequest(
            credentialConfigurationId = "test_config"
        )

        assertEquals("code", params["response_type"])
        assertEquals("test-client", params["client_id"])
        assertNotNull(params["authorization_details"])
        assertEquals(
            "openid_credential",
            authorizationDetail(params["authorization_details"]!!)["type"]?.jsonPrimitive?.content,
        )
        val pkceData = assertNotNull(pkce)
        assertEquals(pkceData.codeChallenge, params["code_challenge"])
    }

    private fun authorizationDetail(params: Parameters) =
        authorizationDetail(params["authorization_details"]!!)

    private fun authorizationDetail(authorizationDetails: String) =
        Json.parseToJsonElement(authorizationDetails).jsonArray.single().jsonObject
}
