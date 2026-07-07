package id.walt.openid4vci

import id.walt.openid4vci.core.OAuth2Provider
import id.walt.openid4vci.core.buildOAuth2Provider
import id.walt.openid4vci.preauthorized.PreAuthorizedCodeIssueRequest
import id.walt.openid4vci.repository.refresh.RefreshTokenRepository
import id.walt.openid4vci.repository.refresh.InMemoryRefreshTokenRepository
import id.walt.openid4vci.requests.authorization.AuthorizationRequestResult
import id.walt.openid4vci.requests.token.AccessTokenRequestResult
import id.walt.openid4vci.responses.authorization.AuthorizationResponseResult
import id.walt.openid4vci.responses.token.AccessTokenResponseResult
import id.walt.openid4vci.tokens.access.AccessTokenIssuer
import id.walt.openid4vci.tokens.refresh.RefreshTokenIssuer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

class ProviderRefreshTokenFlowTest {

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `authorization code flow stores refresh token by signature and refresh rotates it`() = runTest {
        val refreshTokenRepository = InMemoryRefreshTokenRepository()
        val refreshTokenIssuer = TestRefreshTokenIssuer()
        val accessTokenIssuer = CountingTokenIssuer()
        val provider = buildTestProvider(refreshTokenRepository, refreshTokenIssuer, accessTokenIssuer)

        val initial = executeAuthorizationCodeTokenFlow(provider)
        assertEquals(setOf(GrantType.AuthorizationCode.value), initial.accessRequestClientGrantTypes)
        val refreshToken = initial.refreshToken
        val refreshTokenSignature = refreshTokenIssuer.signature(refreshToken)

        assertNull(refreshTokenRepository.get(refreshToken), "Repository must not be keyed by raw refresh token")
        val stored = refreshTokenRepository.get(refreshTokenSignature)
        assertNotNull(stored)
        assertTrue(stored.active)
        assertEquals("demo-client", stored.clientId)
        assertEquals(accessTokenIssuer.signature(initial.accessToken), stored.accessTokenSignature)
        assertEquals(initial.accessRequestId, stored.accessTokenRequest.id)
        assertEquals(setOf("grant_type", "client_id"), stored.accessTokenRequest.requestForm.keys)
        assertFalse(stored.accessTokenRequest.requestForm.containsKey("code"))
        assertFalse(stored.accessTokenRequest.requestForm.containsKey("redirect_uri"))

        val refreshResult = refresh(provider, refreshToken)
        val refreshResponse = refreshResult as AccessTokenResponseResult.Success
        val newRefreshToken = assertNotNull(refreshResponse.response.refreshToken)
        assertNotEquals(refreshToken, newRefreshToken)
        assertNotEquals(initial.accessToken, refreshResponse.response.accessToken)

        val oldAfterRotation = refreshTokenRepository.get(refreshTokenSignature)
        assertNotNull(oldAfterRotation)
        assertFalse(oldAfterRotation.active)

        val newStored = refreshTokenRepository.get(refreshTokenIssuer.signature(newRefreshToken))
        assertNotNull(newStored)
        assertTrue(newStored.active)
        assertEquals(accessTokenIssuer.signature(refreshResponse.response.accessToken), newStored.accessTokenSignature)
        assertEquals(setOf("grant_type", "client_id"), newStored.accessTokenRequest.requestForm.keys)
        assertFalse(newStored.accessTokenRequest.requestForm.containsKey("refresh_token"))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `rotated refresh token cannot be reused`() = runTest {
        val provider = buildTestProvider()
        val initial = executeAuthorizationCodeTokenFlow(provider)

        val firstRefresh = refresh(provider, initial.refreshToken)
        assertTrue(firstRefresh is AccessTokenResponseResult.Success)

        val reuse = refresh(provider, initial.refreshToken)
        assertTrue(reuse is AccessTokenResponseResult.Failure)
        assertEquals("invalid_grant", reuse.error.error)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `refresh token grant fails on client mismatch`() = runTest {
        val provider = buildTestProvider()
        val initial = executeAuthorizationCodeTokenFlow(provider)

        val result = refresh(provider, initial.refreshToken, clientId = "other-client")

        assertTrue(result is AccessTokenResponseResult.Failure)
        assertEquals("invalid_grant", result.error.error)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `refresh token grant accepts client identity resolved outside request body`() = runTest {
        val provider = buildTestProvider()
        val initial = executeAuthorizationCodeTokenFlow(provider)
        val requestResult = provider.createAccessTokenRequest(
            mapOf(
                "grant_type" to listOf(GrantType.RefreshToken.value),
                "refresh_token" to listOf(initial.refreshToken),
            ),
        )
        require(requestResult is AccessTokenRequestResult.Success)

        val parsedClient = requestResult.request.client
        val resolvedClient = DefaultClient(
            id = "demo-client",
            redirectUris = parsedClient.redirectUris,
            grantTypes = parsedClient.grantTypes,
            responseTypes = parsedClient.responseTypes,
            scopes = parsedClient.scopes,
            audience = parsedClient.audience,
        )
        val result = provider.createAccessTokenResponse(
            requestResult.request
                .withClient(resolvedClient)
                .withIssuer("test-issuer"),
        )

        assertTrue(result is AccessTokenResponseResult.Success)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `refresh token grant without resolved client identity uses stored client binding`() = runTest {
        val provider = buildTestProvider()
        val initial = executeAuthorizationCodeTokenFlow(provider)
        val requestResult = provider.createAccessTokenRequest(
            mapOf(
                "grant_type" to listOf(GrantType.RefreshToken.value),
                "refresh_token" to listOf(initial.refreshToken),
            ),
        )
        require(requestResult is AccessTokenRequestResult.Success)

        val result = provider.createAccessTokenResponse(requestResult.request.withIssuer("test-issuer"))

        assertTrue(result is AccessTokenResponseResult.Success)
        assertNotNull(result.response.refreshToken)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `unbound refresh token grant does not require client identity`() = runTest {
        val refreshTokenRepository = InMemoryRefreshTokenRepository()
        val refreshTokenIssuer = TestRefreshTokenIssuer()
        val config = createTestConfig(
            refreshTokenRepository = refreshTokenRepository,
            refreshTokenIssuer = refreshTokenIssuer,
            accessTokenIssuer = CountingTokenIssuer(),
        )
        val provider = buildOAuth2Provider(config)
        val issued = config.preAuthorizedCodeIssuer.issue(
            PreAuthorizedCodeIssueRequest(
                clientId = null,
                scopes = setOf("openid"),
                session = DefaultSession(subject = "anonymous-subject"),
            ),
        )
        val accessRequestResult = provider.createAccessTokenRequest(
            mapOf(
                "grant_type" to listOf(GrantType.PreAuthorizedCode.value),
                "pre-authorized_code" to listOf(issued.code),
            ),
        )
        require(accessRequestResult is AccessTokenRequestResult.Success)
        val accessResponse = provider.createAccessTokenResponse(
            accessRequestResult.request.withIssuer("test-issuer"),
        )
        require(accessResponse is AccessTokenResponseResult.Success)
        val refreshToken = assertNotNull(accessResponse.response.refreshToken)
        val storedRefreshToken = refreshTokenRepository.get(refreshTokenIssuer.signature(refreshToken))
        assertNotNull(storedRefreshToken)
        assertNull(storedRefreshToken.clientId)

        val refreshRequestResult = provider.createAccessTokenRequest(
            mapOf(
                "grant_type" to listOf(GrantType.RefreshToken.value),
                "refresh_token" to listOf(refreshToken),
            ),
        )
        require(refreshRequestResult is AccessTokenRequestResult.Success)
        val refreshResponse = provider.createAccessTokenResponse(
            refreshRequestResult.request.withIssuer("test-issuer"),
        )

        assertTrue(refreshResponse is AccessTokenResponseResult.Success)
        assertNotNull(refreshResponse.response.refreshToken)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `expired refresh token fails`() = runTest {
        val provider = buildTestProvider()
        val initial = executeAuthorizationCodeTokenFlow(
            provider = provider,
            session = DefaultSession(subject = "demo-subject")
                .withExpiresAt(TokenType.REFRESH_TOKEN, Clock.System.now() - 1.seconds),
        )

        val result = refresh(provider, initial.refreshToken)

        assertTrue(result is AccessTokenResponseResult.Failure)
        assertEquals("invalid_grant", result.error.error)
    }

    private fun buildTestProvider(
        refreshTokenRepository: RefreshTokenRepository = InMemoryRefreshTokenRepository(),
        refreshTokenIssuer: RefreshTokenIssuer = TestRefreshTokenIssuer(),
        accessTokenIssuer: AccessTokenIssuer = CountingTokenIssuer(),
    ): OAuth2Provider =
        buildOAuth2Provider(
            createTestConfig(
                accessTokenIssuer = accessTokenIssuer,
                refreshTokenRepository = refreshTokenRepository,
                refreshTokenIssuer = refreshTokenIssuer,
            ),
        )

    private suspend fun executeAuthorizationCodeTokenFlow(
        provider: OAuth2Provider,
        session: Session = DefaultSession(subject = "demo-subject"),
    ): InitialTokenResult {
        val authorizeResult = provider.createAuthorizationRequest(
            mapOf(
                "response_type" to listOf(ResponseType.CODE.value),
                "client_id" to listOf("demo-client"),
                "redirect_uri" to listOf("https://openid4vci.walt.id/callback"),
                "scope" to listOf("openid email"),
            ),
        )
        require(authorizeResult is AuthorizationRequestResult.Success)
        val authorizeRequest = authorizeResult.request.withIssuer("test-issuer")
        val authorizeResponse = provider.createAuthorizationResponse(authorizeRequest, session)
        require(authorizeResponse is AuthorizationResponseResult.Success)

        val accessResult = provider.createAccessTokenRequest(
            mapOf(
                "grant_type" to listOf(GrantType.AuthorizationCode.value),
                "client_id" to listOf("demo-client"),
                "code" to listOf(authorizeResponse.response.code),
                "redirect_uri" to listOf("https://openid4vci.walt.id/callback"),
            ),
        )
        require(accessResult is AccessTokenRequestResult.Success)
        val accessRequest = accessResult.request.withIssuer("test-issuer")
        val accessResponse = provider.createAccessTokenResponse(accessRequest)
        require(accessResponse is AccessTokenResponseResult.Success)

        val refreshToken = accessResponse.response.refreshToken
        require(!refreshToken.isNullOrBlank())

        return InitialTokenResult(
            accessToken = accessResponse.response.accessToken,
            refreshToken = refreshToken,
            accessRequestId = accessRequest.id,
            accessRequestClientGrantTypes = accessRequest.client.grantTypes,
        )
    }

    private suspend fun refresh(
        provider: OAuth2Provider,
        refreshToken: String,
        clientId: String = "demo-client",
    ): AccessTokenResponseResult {
        val requestResult = provider.createAccessTokenRequest(
            mapOf(
                "grant_type" to listOf(GrantType.RefreshToken.value),
                "client_id" to listOf(clientId),
                "refresh_token" to listOf(refreshToken),
            ),
        )
        require(requestResult is AccessTokenRequestResult.Success)
        return provider.createAccessTokenResponse(requestResult.request.withIssuer("test-issuer"))
    }

    private data class InitialTokenResult(
        val accessToken: String,
        val refreshToken: String,
        val accessRequestId: String,
        val accessRequestClientGrantTypes: Set<String>,
    )

    private class CountingTokenIssuer : AccessTokenIssuer {
        private var counter = 0

        override suspend fun issue(claims: Map<String, Any?>): String {
            counter += 1
            return "access-${claims["client_id"]}-$counter"
        }

        override fun signature(token: String): String =
            "access-signature:$token"
    }
}
