package id.waltid.openid4vci.wallet.attestation

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class ClientAttestationChallengeContextTest {
    @Test
    fun discoversOnceAndPreservesIndependentFreshnessValues() = runTest {
        var calls = 0
        val saved = mutableListOf<Pair<String?, String?>>()
        val context = ClientAttestationChallengeContext(onUpdate = { challenge, nonce -> saved += challenge to nonce })
        val client = HttpClient(MockEngine) {
            engine { addHandler { request ->
                calls++
                assertEquals(HttpMethod.Post, request.method)
                assertEquals("application/json", request.headers[HttpHeaders.Accept])
                respond("""{"attestation_challenge":"opaque-challenge","extension":true}""", HttpStatusCode.OK,
                    headersOf("DPoP-Nonce", "separate-nonce"))
            } }
        }
        context.prepare(client, "https://as.example/challenge")
        context.prepare(client, "https://as.example/challenge")
        assertEquals(1, calls)
        assertEquals("opaque-challenge", context.challenge)
        assertEquals("separate-nonce", context.dpopNonce)
        context.observe(headersOf("oauth-client-attestation-challenge", "response-challenge"))
        context.observe(headersOf("DPoP-Nonce", "next-nonce"))
        context.observe(headersOf("OAuth-Client-Attestation-Challenge", " "))
        assertEquals("response-challenge", context.challenge)
        assertEquals("next-nonce", context.dpopNonce)
        assertEquals(listOf<Pair<String?, String?>>("opaque-challenge" to "separate-nonce", "response-challenge" to "separate-nonce",
            "response-challenge" to "next-nonce"), saved)
        assertFalse(context.toString().contains("response-challenge"))
        client.close()
    }

    @Test
    fun restoredChallengeCannotSuppressDiscoveryAndCancellationPropagates() = runTest {
        val client = HttpClient(MockEngine) { engine { addHandler { throw CancellationException("cancelled") } } }
        val restored = ClientAttestationChallengeContext("restored", "nonce")
        assertFailsWith<CancellationException> { restored.prepare(client, "https://as.example/challenge") }
        assertEquals("restored", restored.challenge)
        assertFailsWith<CancellationException> { ClientAttestationChallengeContext().prepare(client, "https://as.example/challenge") }
        client.close()
    }

    @Test
    fun responseOnlyServerAndContextsRemainIndependent() = runTest {
        val client = HttpClient(MockEngine) { engine { addHandler { error("No discovery expected") } } }
        val first = ClientAttestationChallengeContext()
        val second = ClientAttestationChallengeContext()
        first.prepare(client, null)
        assertNull(first.challenge)
        first.observe(headersOf("OAuth-Client-Attestation-Challenge", "from-error"))
        assertEquals("from-error", first.challenge)
        assertNull(second.challenge)
        client.close()
    }
}
