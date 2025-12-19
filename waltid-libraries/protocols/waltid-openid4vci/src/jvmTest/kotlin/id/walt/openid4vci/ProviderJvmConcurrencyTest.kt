package id.walt.openid4vci

import id.walt.openid4vci.core.Config
import id.walt.openid4vci.core.OAuth2Provider
import id.walt.openid4vci.core.buildProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import id.walt.openid4vci.core.AccessRequestResult
import id.walt.openid4vci.core.AccessResponseResult
import id.walt.openid4vci.core.AuthorizeRequestResult
import id.walt.openid4vci.core.AuthorizeResponseResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime


class ProviderJvmConcurrencyTest {

    @Test
    fun `authorization code flow handles real dispatcher concurrency`() = runBlocking {
        val provider = buildProvider(defaultConcurrencyTestConfig())
        // Uncomment to experiment with unsafe repositories and observe race conditions:
//         val provider = buildProvider(defaultConcurrencyTestConfig().copy(authorizationCodeRepository = UnsafeAuthorizationCodeRepository()))

        runConcurrentAuthCodeFlows(
            provider = provider,
            iterations = 20,
            parallelism = 500,
            dispatcher = Dispatchers.Default,
        )
    }
}

internal fun defaultConcurrencyTestConfig(): Config = createTestConfig()

internal suspend fun runConcurrentAuthCodeFlows(
    provider: OAuth2Provider,
    iterations: Int,
    parallelism: Int,
    dispatcher: CoroutineDispatcher? = null,
) {
    repeat(iterations) { iteration ->
        val accessTokens = coroutineScope {
            (0 until parallelism).map { idx ->
                if (dispatcher != null) {
                    async(dispatcher) { executeAuthorizationCodeFlow(provider, iteration, idx) }
                } else {
                    async { executeAuthorizationCodeFlow(provider, iteration, idx) }
                }
            }.awaitAll()
        }

        assertEquals(accessTokens.size, accessTokens.toSet().size)
        assertTrue(accessTokens.all { it.startsWith("access-") })
    }
}

@OptIn(ExperimentalTime::class)
internal suspend fun executeAuthorizationCodeFlow(
    provider: OAuth2Provider,
    iteration: Int,
    idx: Int,
): String {
    val expectedSubject = "subject-$iteration-$idx"
    val clientId = "concurrent-client-$iteration-$idx"
    val state = "state-$iteration-$idx"
    val issuerId = "issuer-$iteration"
    val authorizeParams = mapOf(
        "response_type" to "code",
        "client_id" to clientId,
        "redirect_uri" to "https://client.example/callback",
        "scope" to "myscope",
        "state" to state,
    )

    val authorizeRequest = provider.createAuthorizeRequest(authorizeParams)
    require(authorizeRequest is AuthorizeRequestResult.Success)
    authorizeRequest.request.setIssuerId(issuerId)

    val authorizeResponse = provider.createAuthorizeResponse(
        authorizeRequest.request,
        DefaultSession(subject = expectedSubject),
    )
    require(authorizeResponse is AuthorizeResponseResult.Success)
    val code = authorizeResponse.response.parameters.getValue("code")

    val accessRequestResult = provider.createAccessRequest(
        mapOf(
            "grant_type" to GRANT_TYPE_AUTHORIZATION_CODE,
            "client_id" to clientId,
            "code" to code,
            "redirect_uri" to "https://client.example/callback",
        ),
        DefaultSession(),
    )
    require(accessRequestResult is AccessRequestResult.Success)
    accessRequestResult.request.setIssuerId(issuerId)
    val accessResponse = provider.createAccessResponse(accessRequestResult.request)
    require(accessResponse is AccessResponseResult.Success)

    assertEquals(expectedSubject, accessRequestResult.request.getSession()?.getSubject())
    assertEquals(clientId, accessRequestResult.request.getClient().id)
    assertEquals(state, authorizeRequest.request.state)

    val token = accessResponse.response.accessToken
    assertTrue(token.startsWith("access-$clientId-$code"))
    return token
}
