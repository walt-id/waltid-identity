package id.walt.openid4vci

import id.walt.openid4vci.core.AccessRequestResult
import id.walt.openid4vci.core.AccessResponseResult
import id.walt.openid4vci.core.AuthorizeRequestResult
import id.walt.openid4vci.core.AuthorizeResponseResult
import id.walt.openid4vci.core.buildOAuth2Provider
import id.walt.openid4vci.preauthorized.PreAuthorizedCodeIssueRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.seconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

class ProviderCombinedFlowTest {

    @OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)
    @Test
    fun `provider with both handlers supports authorization and pre-authorized flows`() = runTest {
        val config = createTestConfig()
        val issuerId = "test-issuer"

        val provider = buildOAuth2Provider(config)

        // Authorization code flow
        val authorizeRequestResult = provider.createAuthorizeRequest(
            mapOf(
                "response_type" to "code",
                "client_id" to "demo-client",
                "redirect_uri" to "https://openid4vci.walt.id/callback",
                "scope" to "openid",
                "state" to "abc",
            ),
        )
        assertTrue(authorizeRequestResult.isSuccess())
        val authorizeRequest = (authorizeRequestResult as AuthorizeRequestResult.Success).request.also {
            it.setIssuerId(issuerId)
        }

        val session = DefaultSession(subject = "demo-subject")
        val authorizeResponse = provider.createAuthorizeResponse(authorizeRequest, session)
        assertTrue(authorizeResponse.isSuccess())
        val response = (authorizeResponse as AuthorizeResponseResult.Success).response
        val code = response.parameters.getValue("code")

        val accessResult = provider.createAccessRequest(
            mapOf(
                "grant_type" to GRANT_TYPE_AUTHORIZATION_CODE,
                "client_id" to "demo-client",
                "code" to code,
                "redirect_uri" to "https://openid4vci.walt.id/callback",
            ),
            DefaultSession(),
        )
        assertTrue(accessResult.isSuccess())
        val accessRequest = (accessResult as AccessRequestResult.Success).request.also {
            it.setIssuerId(issuerId)
        }
        val accessResponse = provider.createAccessResponse(accessRequest)
        assertTrue(accessResponse.isSuccess())
        val tokenResponse = (accessResponse as AccessResponseResult.Success).response
        assertTrue(tokenResponse.accessToken.isNotBlank())

        // Pre-authorized code flow
        val issuedCode = config.preAuthorizedCodeIssuer.issue(
            PreAuthorizedCodeIssueRequest(
                clientId = "pre-client",
                scopes = setOf("openid"),
                audience = setOf("aud:issuer"),
                session = DefaultSession().apply { setSubject("pre-subject") },
                credentialNonce = "nonce-pre",
                credentialNonceExpiresAt = kotlin.time.Clock.System.now() + 600.seconds,
            ),
        )
        val preCode = issuedCode.code

        val preAccessResult = provider.createAccessRequest(
            mapOf(
                "grant_type" to GRANT_TYPE_PRE_AUTHORIZED_CODE,
                "pre-authorized_code" to preCode,
            ),
            DefaultSession(),
        )
        assertTrue(preAccessResult.isSuccess())
        val preAccessRequest = (preAccessResult as AccessRequestResult.Success).request.also {
            it.setIssuerId(issuerId)
        }
        val preAccessResponse = provider.createAccessResponse(preAccessRequest)
        assertTrue(preAccessResponse.isSuccess())
        val preTokenResponse = (preAccessResponse as AccessResponseResult.Success).response
        assertEquals("nonce-pre", preTokenResponse.extra["c_nonce"])
        assertNull(config.preAuthorizedCodeRepository.get(preCode))
    }
}
