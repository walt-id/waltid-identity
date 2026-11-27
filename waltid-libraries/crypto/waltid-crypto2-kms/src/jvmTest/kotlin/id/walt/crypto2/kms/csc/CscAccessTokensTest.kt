package id.walt.crypto2.kms.csc

import id.walt.crypto2.kms.CredentialReference
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class CscAccessTokensTest {
    private val credentials = CscCredentials(CLIENT_ID, CLIENT_SECRET)

    @Test
    fun `a slow token endpoint does not block other credentials`() = runTest {
        val slowStarted = CompletableDeferred<Unit>()
        val releaseSlow = CompletableDeferred<Unit>()
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    if (request.url.host == "slow.example") {
                        slowStarted.complete(Unit)
                        releaseSlow.await()
                    }
                    respondToken("token-${request.url.host}")
                }
            }
        }
        val tokens = CscAccessTokens(client) { NOW }

        // Real time: a regression would block the second call until the timeout.
        withContext(Dispatchers.Default) {
            withTimeout(5.seconds) {
                val slow = async {
                    tokens.accessToken("https://slow.example/oauth2/token", CredentialReference("slow"), credentials)
                }
                slowStarted.await()
                assertEquals(
                    "token-fast.example",
                    tokens.accessToken("https://fast.example/oauth2/token", CredentialReference("fast"), credentials),
                )
                releaseSlow.complete(Unit)
                assertEquals("token-slow.example", slow.await())
            }
        }
    }

    @Test
    fun `invalidate drops only the token that failed`() = runTest {
        var issued = 0
        val client = HttpClient(MockEngine) { engine { addHandler { respondToken("token-${++issued}") } } }
        val tokens = CscAccessTokens(client) { NOW }
        val endpoint = "https://rssp.example/oauth2/token"
        val reference = CredentialReference("qtsp-seal")

        assertEquals("token-1", tokens.accessToken(endpoint, reference, credentials))
        tokens.invalidate(endpoint, reference, "token-0")
        assertEquals("token-1", tokens.accessToken(endpoint, reference, credentials))
        tokens.invalidate(endpoint, reference, "token-1")
        assertEquals("token-2", tokens.accessToken(endpoint, reference, credentials))
        assertEquals(2, issued)
    }

    private fun MockRequestHandleScope.respondToken(token: String) =
        respond(
            """{"access_token":"$token","token_type":"Bearer","expires_in":3600}""",
            HttpStatusCode.OK,
            headersOf(HttpHeaders.ContentType, "application/json"),
        )
}
