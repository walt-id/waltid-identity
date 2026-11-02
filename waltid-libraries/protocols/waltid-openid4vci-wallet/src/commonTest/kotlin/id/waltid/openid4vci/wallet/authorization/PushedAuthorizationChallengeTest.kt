package id.waltid.openid4vci.wallet.authorization

import id.waltid.openid4vci.wallet.attestation.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import id.walt.crypto2.jose.CompactJws
import kotlin.test.*

class PushedAuthorizationChallengeTest {
    @Test
    fun responseChallengeRebuildsSignedPopAndKeepsParParameters() = runTest {
        val key = attestationTestKey("par-challenge")
        val context = ClientAttestationChallengeContext()
        val pops = mutableListOf<String>()
        val bodies = mutableListOf<String>()
        val client = HttpClient(MockEngine) { engine { addHandler { request ->
            pops += requireNotNull(request.headers[ClientAttestationHeaders.HEADER_ATTESTATION_POP])
            bodies += (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            assertEquals("attestation", request.headers[ClientAttestationHeaders.HEADER_ATTESTATION])
            if (pops.size == 1) respond("""{"error":"use_attestation_challenge"}""", HttpStatusCode.BadRequest,
                headersOf("OAuth-Client-Attestation-Challenge", "retry-challenge"))
            else respond("""{"request_uri":"urn:par:1","expires_in":60}""", HttpStatusCode.Created,
                headersOf("OAuth-Client-Attestation-Challenge", "for-token"))
        } } }
        val response = PushedAuthorizationRequestExecutor.execute(client, "https://as.example/par",
            mapOf("client_id" to "wallet", "state" to "state", "code_challenge" to "pkce", "redirect_uri" to "https://wallet.example/cb"),
            onResponseHeaders = context::observe,
            attestationHeadersFactory = { ClientAttestationHeaders("attestation",
                WalletAttestationPopBuilder().buildPopJwt(key, "wallet", "https://as.example", context.challenge)) })
        assertEquals("urn:par:1", response.requestUri)
        assertEquals(2, pops.size)
        assertEquals(bodies[0], bodies[1])
        fun claims(jwt: String) = Json.parseToJsonElement(CompactJws.decodeUnverified(jwt).payload.decodeToString()).jsonObject
        assertNull(claims(pops[0])["challenge"])
        assertEquals("retry-challenge", claims(pops[1])["challenge"]?.jsonPrimitive?.content)
        assertNotEquals(claims(pops[0])["jti"], claims(pops[1])["jti"])
        assertEquals("https://as.example", claims(pops[1])["aud"]?.jsonPrimitive?.content)
        assertEquals("for-token", context.challenge)
        client.close()
    }

    @Test
    fun invalidResponsesAndStaticProofsDoNotRetry() = runTest {
        for ((status, body, challenge) in listOf(
            Triple(HttpStatusCode.BadRequest, """{"error":"use_attestation_challenge"}""", ""),
            Triple(HttpStatusCode.BadRequest, "not-json", "challenge"),
            Triple(HttpStatusCode.BadRequest, """{"error":"invalid_client"}""", "challenge"),
            Triple(HttpStatusCode.Unauthorized, """{"error":"use_attestation_challenge"}""", "challenge"),
        )) {
            var calls = 0
            val client = HttpClient(MockEngine) { engine { addHandler {
                calls++
                respond(body, status, headersOf("OAuth-Client-Attestation-Challenge", challenge))
            } } }
            assertFailsWith<IllegalArgumentException> {
                PushedAuthorizationRequestExecutor.execute(client, "https://as.example/par", emptyMap(),
                    attestationHeadersFactory = { ClientAttestationHeaders("attestation", "pop") })
            }
            assertEquals(1, calls)
            client.close()
        }
        var calls = 0
        val client = HttpClient(MockEngine) { engine { addHandler {
            calls++
            respond("""{"error":"use_attestation_challenge"}""", HttpStatusCode.BadRequest,
                headersOf("OAuth-Client-Attestation-Challenge", "challenge"))
        } } }
        assertFailsWith<IllegalArgumentException> {
            PushedAuthorizationRequestExecutor.execute(client, "https://as.example/par", emptyMap(),
                attestationHeaders = ClientAttestationHeaders("attestation", "static-pop"))
        }
        assertEquals(1, calls)
        client.close()
    }

    @Test
    fun repeatedChallengeStopsAfterTwoAttemptsAndCancellationIsNotRetried() = runTest {
        var calls = 0
        val client = HttpClient(MockEngine) { engine { addHandler {
            calls++
            respond("""{"error":"use_attestation_challenge"}""", HttpStatusCode.BadRequest,
                headersOf("OAuth-Client-Attestation-Challenge", "challenge-$calls"))
        } } }
        assertFailsWith<IllegalArgumentException> {
            PushedAuthorizationRequestExecutor.execute(client, "https://as.example/par", emptyMap(),
                attestationHeadersFactory = { ClientAttestationHeaders("attestation", "pop") })
        }
        assertEquals(2, calls)
        client.close()
        calls = 0
        val cancelled = HttpClient(MockEngine) { engine { addHandler { calls++; throw CancellationException() } } }
        assertFailsWith<CancellationException> {
            PushedAuthorizationRequestExecutor.execute(cancelled, "https://as.example/par", emptyMap(),
                attestationHeadersFactory = { ClientAttestationHeaders("attestation", "pop") })
        }
        assertEquals(1, calls)
        cancelled.close()
    }
}
