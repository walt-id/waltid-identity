package id.walt.crypto2.kms

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class JsonHttpClientTest {
    @Test
    fun `request cancellation propagates`() = runTest {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { throw CancellationException("cancelled") }
            }
        }

        assertFailsWith<CancellationException> {
            client.executeJson("test KMS", "https://kms.example", HttpMethod.Post)
        }
    }

    @Test
    fun `an error status keeps the status but not the response body when the client validates responses`() = runTest {
        val clients = mapOf(
            "expectSuccess" to HttpClient(MockEngine) {
                expectSuccess = true
                engine { addHandler { respondEcho() } }
            },
            "custom validator" to HttpClient(MockEngine) {
                engine { addHandler { respondEcho() } }
                HttpResponseValidator {
                    validateResponse { response ->
                        if (!response.status.isSuccess()) throw ClientRequestException(response, response.bodyAsText())
                    }
                }
            },
        )

        clients.forEach { (name, client) ->
            val failure = assertFailsWith<KmsProviderException>(name) {
                client.executeJson("test KMS", "https://kms.example", HttpMethod.Post)
            }
            assertEquals(401, failure.httpStatus, name)
            val messages = generateSequence<Throwable>(failure) { it.cause }.mapNotNull { it.message }.toList()
            assertFalse(messages.any { ECHOED_SECRET in it }, "$name exposes the response body: $messages")
        }
    }

    private fun MockRequestHandleScope.respondEcho() = respond(
        """{"error":"invalid_client","error_description":"Unknown secret $ECHOED_SECRET"}""",
        HttpStatusCode.Unauthorized,
        headersOf(HttpHeaders.ContentType, "application/json"),
    )

    private companion object {
        const val ECHOED_SECRET = "echoed-client-secret"
    }
}
