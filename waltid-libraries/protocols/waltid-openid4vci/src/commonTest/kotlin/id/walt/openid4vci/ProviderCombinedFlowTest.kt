package id.walt.openid4vci

import id.walt.openid4vci.core.AccessRequestResult
import id.walt.openid4vci.core.AccessResponseResult
import id.walt.openid4vci.core.AuthorizeRequestResult
import id.walt.openid4vci.core.AuthorizeResponseResult
import id.walt.openid4vci.core.buildOAuth2Provider
import id.walt.openid4vci.core.AccessTokenResponse
import id.walt.openid4vci.core.TOKEN_TYPE_BEARER
import id.walt.openid4vci.preauthorized.PreAuthorizedCodeIssueRequest
import id.walt.openid4vci.request.AccessTokenRequest
import id.walt.openid4vci.request.AuthorizationRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.seconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertNotEquals

class ProviderCombinedFlowTest {

    @OptIn(ExperimentalCoroutinesApi::class)
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
                "grant_type" to GrantType.AuthorizationCode.value,
                "client_id" to "demo-client",
                "code" to code,
                "redirect_uri" to "https://openid4vci.walt.id/callback",
            ),
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
                session = DefaultSession(subject = "pre-subject"),
                credentialNonce = "nonce-pre",
                credentialNonceExpiresAt = kotlin.time.Clock.System.now() + 600.seconds,
            ),
        )
        val preCode = issuedCode.code

        val preAccessResult = provider.createAccessRequest(
            mapOf(
                "grant_type" to GrantType.PreAuthorizedCode.value,
                "pre-authorized_code" to preCode,
            ),
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

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `same subject can mint distinct tokens for different clients`() = runTest {
        val config = createTestConfig()
        val provider = buildOAuth2Provider(config)

        val issuerId = "issuer-multi"
        val subject = "user-123"

        suspend fun authorizeFor(clientId: String, redirectUri: String, scope: String, state: String): Pair<String, AuthorizationRequest> {
            val authorizeResult = provider.createAuthorizeRequest(
                mapOf(
                    "response_type" to "code",
                    "client_id" to clientId,
                    "redirect_uri" to redirectUri,
                    "scope" to scope,
                    "state" to state,
                ),
            )
            assertTrue(authorizeResult is AuthorizeRequestResult.Success, "authorize request failed for $clientId")
            val authorizeReq = (authorizeResult).request.also {
                it.setIssuerId(issuerId)
            }
            val expectedScopes = scope.split(" ").filter { it.isNotBlank() }.toSet()

            // Request assertions
            assertEquals(issuerId, authorizeReq.getIssuerId(), "issuer must be set on authorize request")
            assertEquals(clientId, authorizeReq.getClient().id, "client id must be preserved")
            assertEquals(redirectUri, authorizeReq.redirectUri, "redirect_uri must be set on request")
            assertEquals(state, authorizeReq.state, "state must be preserved")
            assertEquals(setOf("code"), authorizeReq.getResponseTypes().toSet(), "response_type must be code")
            assertEquals(expectedScopes, authorizeReq.getRequestedScopes().toSet(), "scopes must be captured from request")
            assertEquals(ResponseModeType.QUERY, authorizeReq.responseMode)
            assertEquals(ResponseModeType.QUERY, authorizeReq.defaultResponseMode)
            assertEquals(clientId, authorizeReq.getRequestForm().getFirst("client_id"))
            assertEquals(redirectUri, authorizeReq.getRequestForm().getFirst("redirect_uri"))

            val authorizeResponse = provider.createAuthorizeResponse(
                authorizeReq,
                DefaultSession(subject = subject),
            )
            assertTrue(authorizeResponse is AuthorizeResponseResult.Success, "authorize response failed for $clientId")

            val response = (authorizeResponse).response
            val code = response.parameters["code"] ?: error("missing code for $clientId")
            assertEquals(state, response.parameters["state"])
            assertEquals(redirectUri, response.redirectUri, "response redirect must match request")
            assertEquals(ResponseModeType.QUERY, response.responseMode)
            val responseScopes = response.parameters["scope"]?.split(" ")?.filter { it.isNotBlank() }?.toSet().orEmpty()
            assertEquals(expectedScopes, responseScopes, "granted scopes must be reflected in authorize response")
            return code to authorizeReq
        }

        suspend fun exchangeCode(
            clientId: String,
            redirectUri: String,
            code: String,
            expectedScopes: Set<String>,
        ): Pair<AccessTokenResponse, AccessTokenRequest> {
            val accessResult = provider.createAccessRequest(
                mapOf(
                    "grant_type" to GrantType.AuthorizationCode.value,
                    "client_id" to clientId,
                    "code" to code,
                    "redirect_uri" to redirectUri,
                ),
            )
            assertTrue(accessResult is AccessRequestResult.Success, "access request failed for $clientId")
            val accessRequest = (accessResult).request.also {
                it.setIssuerId(issuerId)
            }
            assertEquals(issuerId, accessRequest.getIssuerId())
            assertEquals(setOf(GrantType.AuthorizationCode.value), accessRequest.getGrantTypes().toSet())
            assertEquals(code, accessRequest.getRequestForm().getFirst("code"))
            assertEquals(redirectUri, accessRequest.getRequestForm().getFirst("redirect_uri"))

            val accessResponse = provider.createAccessResponse(accessRequest)
            assertTrue(accessResponse is AccessResponseResult.Success, "access response failed for $clientId")
            val tokenResponse = (accessResponse).response

            assertTrue(accessRequest.hasHandledGrantType(GrantType.AuthorizationCode.value))
            assertEquals(subject, accessRequest.getSession()?.getSubject(), "session subject must survive round trip for $clientId")
            assertEquals(clientId, accessRequest.getClient().id, "client must be preserved for $clientId")
            assertEquals(expectedScopes, accessRequest.getGrantedScopes().toSet(), "granted scopes must match request for $clientId")
            assertNotNull(accessRequest.getSession(), "session must be restored on access request")

            assertEquals(TOKEN_TYPE_BEARER, tokenResponse.tokenType)
            assertNull(tokenResponse.expiresIn)
            assertTrue(tokenResponse.extra.isEmpty())
            return tokenResponse to accessRequest
        }

        val (codeA, _) = authorizeFor(
            clientId = "app-a",
            redirectUri = "https://client-a.example.org/callback",
            scope = "openid email",
            state = "state-a",
        )
        val (codeB, _) = authorizeFor(
            clientId = "app-b",
            redirectUri = "https://client-b.example.org/callback",
            scope = "openid profile",
            state = "state-b",
        )

        assertTrue(codeA.isNotBlank())
        assertTrue(codeB.isNotBlank())
        assertNotEquals(codeA, codeB, "authorization codes must be unique per client")

        val (tokenA, accessReqA) = exchangeCode(
            clientId = "app-a",
            redirectUri = "https://client-a.example.org/callback",
            code = codeA,
            expectedScopes = setOf("openid", "email"),
        )
        val (tokenB, accessReqB) = exchangeCode(
            clientId = "app-b",
            redirectUri = "https://client-b.example.org/callback",
            code = codeB,
            expectedScopes = setOf("openid", "profile"),
        )

        assertTrue(tokenA.accessToken.isNotBlank())
        assertTrue(tokenB.accessToken.isNotBlank())
        assertNotEquals(tokenA.accessToken, tokenB.accessToken, "tokens must differ per client")

        // Codes are one-time use
        assertNull(config.authorizationCodeRepository.consume(codeA))
        assertNull(config.authorizationCodeRepository.consume(codeB))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `parallel authorization_code flows stay isolated per client`() = runTest {
        val config = createTestConfig()
        val provider = buildOAuth2Provider(config)

        val issuerId = "issuer-parallel"
        val subject = "user-parallel"

        suspend fun runFlow(clientId: String, redirectUri: String, scope: String, state: String): Pair<String, String> {
            val authorizeResult = provider.createAuthorizeRequest(
                mapOf(
                    "response_type" to "code",
                    "client_id" to clientId,
                    "redirect_uri" to redirectUri,
                    "scope" to scope,
                    "state" to state,
                ),
            )
            assertTrue(authorizeResult is AuthorizeRequestResult.Success, "authorize request failed for $clientId")
            val authorizeReq = (authorizeResult).request.also {
                it.setIssuerId(issuerId)
            }

            val authorizeResponse = provider.createAuthorizeResponse(
                authorizeReq,
                DefaultSession(subject = subject),
            )
            assertTrue(authorizeResponse is AuthorizeResponseResult.Success, "authorize response failed for $clientId")
            val response = (authorizeResponse).response
            val code = response.parameters["code"] ?: error("missing code for $clientId")

            val accessResult = provider.createAccessRequest(
                mapOf(
                    "grant_type" to GrantType.AuthorizationCode.value,
                    "client_id" to clientId,
                    "code" to code,
                    "redirect_uri" to redirectUri,
                ),
            )
            assertTrue(accessResult is AccessRequestResult.Success, "access request failed for $clientId")
            val accessRequest = (accessResult).request.also {
                it.setIssuerId(issuerId)
            }

            val accessResponse = provider.createAccessResponse(accessRequest)
            assertTrue(accessResponse is AccessResponseResult.Success, "access response failed for $clientId")
            val tokenResponse = (accessResponse).response

            return code to tokenResponse.accessToken
        }

        val flowA = async {
            runFlow(
                clientId = "app-parallel-a",
                redirectUri = "https://client-a.example.org/callback",
                scope = "openid email",
                state = "state-a",
            )
        }

        val flowB = async {
            runFlow(
                clientId = "app-parallel-b",
                redirectUri = "https://client-b.example.org/callback",
                scope = "openid profile",
                state = "state-b",
            )
        }

        // collect results
        val resultA = flowA.await()
        val resultB = flowB.await()

        val (finalCodeA, finalTokenA) = resultA
        val (finalCodeB, finalTokenB) = resultB

        assertNotEquals(finalCodeA, finalCodeB, "codes must be unique across parallel clients")
        assertNotEquals(finalTokenA, finalTokenB, "tokens must be unique across parallel clients")

        // codes should be consumed after exchange
        assertNull(config.authorizationCodeRepository.consume(finalCodeA))
        assertNull(config.authorizationCodeRepository.consume(finalCodeB))
    }
}
