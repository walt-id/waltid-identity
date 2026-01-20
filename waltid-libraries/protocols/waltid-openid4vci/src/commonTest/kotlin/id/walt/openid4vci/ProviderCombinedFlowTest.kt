package id.walt.openid4vci

import id.walt.openid4vci.responses.token.AccessResponseResult
import id.walt.openid4vci.responses.authorization.AuthorizationResponseResult
import id.walt.openid4vci.core.buildOAuth2Provider
import id.walt.openid4vci.responses.token.AccessTokenResponse
import id.walt.openid4vci.core.TOKEN_TYPE_BEARER
import id.walt.openid4vci.preauthorized.PreAuthorizedCodeIssueRequest
import id.walt.openid4vci.requests.token.AccessTokenRequest
import id.walt.openid4vci.requests.authorization.AuthorizationRequest
import id.walt.openid4vci.requests.authorization.AuthorizationRequestResult
import id.walt.openid4vci.requests.token.AccessTokenRequestResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.seconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertNotEquals

class ProviderCombinedFlowTest {

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `provider with both handlers supports authorization and pre-authorized flows`() = runTest {
        val config = createTestConfig()
        val issClaim = "test-issuer"

        val provider = buildOAuth2Provider(config)

        // Authorization code flow
        val AuthorizationRequestResult = provider.createAuthorizationRequest(
            mapOf(
                "response_type" to listOf("code"),
                "client_id" to listOf("demo-client"),
                "redirect_uri" to listOf("https://openid4vci.walt.id/callback"),
                "scope" to listOf("openid"),
                "state" to listOf("abc"),
            ),
        )
        assertTrue(AuthorizationRequestResult.isSuccess())
        val authorizeRequest = (AuthorizationRequestResult as AuthorizationRequestResult.Success).request.withIssuer(issClaim)

        val session = DefaultSession(subject = "demo-subject")
        val authorizeResponse = provider.createAuthorizationResponse(authorizeRequest, session)
        assertTrue(authorizeResponse.isSuccess())
        val response = (authorizeResponse as AuthorizationResponseResult.Success).response
        val code = response.code

        val accessResult = provider.createAccessTokenRequest(
            mapOf(
                "grant_type" to listOf(GrantType.AuthorizationCode.value),
                "client_id" to listOf("demo-client"),
                "code" to listOf(code),
                "redirect_uri" to listOf("https://openid4vci.walt.id/callback"),
            ),
        )
        assertTrue(accessResult.isSuccess())
        val accessRequest = (accessResult as AccessTokenRequestResult.Success).request.withIssuer(issClaim)
        val accessResponse = provider.createAccessTokenResponse(accessRequest)
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

        val preAccessResult = provider.createAccessTokenRequest(
            mapOf(
                "grant_type" to listOf(GrantType.PreAuthorizedCode.value),
                "pre-authorized_code" to listOf(preCode),
            ),
        )
        assertTrue(preAccessResult.isSuccess())
        val preAccessRequest = (preAccessResult as AccessTokenRequestResult.Success).request.withIssuer(issClaim)
        val preAccessResponse = provider.createAccessTokenResponse(preAccessRequest)
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

        val issClaim = "issuer-multi"
        val subject = "user-123"

        suspend fun authorizeFor(clientId: String, redirectUri: String, scope: String, state: String): Pair<String, AuthorizationRequest> {
            val authorizeResult = provider.createAuthorizationRequest(
                mapOf(
                    "response_type" to listOf("code"),
                    "client_id" to listOf(clientId),
                    "redirect_uri" to listOf(redirectUri),
                    "scope" to listOf(scope),
                    "state" to listOf(state),
                ),
            )
            assertTrue(authorizeResult is AuthorizationRequestResult.Success, "authorize request failed for $clientId")
            val authorizeReq = authorizeResult.request.withIssuer(issClaim)
            val expectedScopes = scope.split(" ").filter { it.isNotBlank() }.toSet()

            // Request assertions
            assertEquals(issClaim, authorizeReq.issClaim, "issuer must be set on authorize request")
            assertEquals(clientId, authorizeReq.client.id, "client id must be preserved")
            assertEquals(redirectUri, authorizeReq.redirectUri, "redirect_uri must be set on request")
            assertEquals(state, authorizeReq.state, "state must be preserved")
            assertEquals(setOf("code"), authorizeReq.responseTypes.toSet(), "response_type must be code")
            assertEquals(expectedScopes, authorizeReq.requestedScopes.toSet(), "scopes must be captured from request")
            assertEquals(ResponseModeType.QUERY, authorizeReq.responseMode)
            assertEquals(ResponseModeType.QUERY, authorizeReq.defaultResponseMode)
            assertEquals(clientId, authorizeReq.requestForm["client_id"]?.firstOrNull())
            assertEquals(redirectUri, authorizeReq.requestForm["redirect_uri"]?.firstOrNull())

            val authorizeResponse = provider.createAuthorizationResponse(
                authorizeReq,
                DefaultSession(subject = subject),
            )
            assertTrue(authorizeResponse is AuthorizationResponseResult.Success, "authorize response failed for $clientId")

            val response = authorizeResponse.response
            val code = response.code
            assertEquals(state, response.state)
            assertEquals(redirectUri, response.redirectUri, "response redirect must match request")
            assertEquals(ResponseModeType.QUERY, response.responseMode)
            val responseScopes = response.scope
                ?.split(" ")
                ?.filter { it.isNotBlank() }
                ?.toSet()
                .orEmpty()
            assertEquals(expectedScopes, responseScopes, "granted scopes must be reflected in authorize response")
            return code to authorizeReq
        }

        suspend fun exchangeCode(
            clientId: String,
            redirectUri: String,
            code: String,
            expectedScopes: Set<String>,
        ): Pair<AccessTokenResponse, AccessTokenRequest> {
            val accessResult = provider.createAccessTokenRequest(
                mapOf(
                    "grant_type" to listOf(GrantType.AuthorizationCode.value),
                    "client_id" to listOf(clientId),
                    "code" to listOf(code),
                    "redirect_uri" to listOf(redirectUri),
                ),
            )
            assertTrue(accessResult is AccessTokenRequestResult.Success, "access request failed for $clientId")
            val accessRequest = accessResult.request.withIssuer(issClaim)
            assertEquals(issClaim, accessRequest.issClaim)
            assertEquals(setOf(GrantType.AuthorizationCode.value), accessRequest.grantTypes.toSet())
            assertEquals(code, accessRequest.requestForm["code"]?.firstOrNull())
            assertEquals(redirectUri, accessRequest.requestForm["redirect_uri"]?.firstOrNull())

            val accessResponse = provider.createAccessTokenResponse(accessRequest)
            assertTrue(accessResponse is AccessResponseResult.Success, "access response failed for $clientId")
            val success = accessResponse as AccessResponseResult.Success
            val tokenResponse = success.response
            val updatedRequest = success.request

            assertEquals(subject, updatedRequest.session?.subject, "session subject must survive round trip for $clientId")
            assertEquals(clientId, updatedRequest.client.id, "client must be preserved for $clientId")
            assertEquals(expectedScopes, updatedRequest.grantedScopes.toSet(), "granted scopes must match request for $clientId")

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

        val issClaim = "issuer-parallel"
        val subject = "user-parallel"

        suspend fun runFlow(clientId: String, redirectUri: String, scope: String, state: String): Pair<String, String> {
            val authorizeResult = provider.createAuthorizationRequest(
                mapOf(
                    "response_type" to listOf("code"),
                    "client_id" to listOf(clientId),
                    "redirect_uri" to listOf(redirectUri),
                    "scope" to listOf(scope),
                    "state" to listOf(state),
                ),
            )
            assertTrue(authorizeResult is AuthorizationRequestResult.Success, "authorize request failed for $clientId")
            val authorizeReq = authorizeResult.request.withIssuer(issClaim)

        val authorizeResponse = provider.createAuthorizationResponse(
            authorizeReq,
            DefaultSession(subject = subject),
        )
        assertTrue(authorizeResponse is AuthorizationResponseResult.Success, "authorize response failed for $clientId")
        val response = authorizeResponse.response
        val code = response.code

            val accessResult = provider.createAccessTokenRequest(
                mapOf(
                    "grant_type" to listOf(GrantType.AuthorizationCode.value),
                    "client_id" to listOf(clientId),
                    "code" to listOf(code),
                    "redirect_uri" to listOf(redirectUri),
                ),
            )
            assertTrue(accessResult is AccessTokenRequestResult.Success, "access request failed for $clientId")
            val accessRequest = accessResult.request.withIssuer(issClaim)

            val accessResponse = provider.createAccessTokenResponse(accessRequest)
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
