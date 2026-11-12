@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package id.walt.credentials.status

import id.walt.policies2.vc.policies.status.StatusListContent
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class StatusListTransportTest {
    @Test
    fun conditionalForceRevalidatesAndDerivedClientCannotInheritWalletCredentials() = runTest {
        var at = Instant.fromEpochSeconds(1_800_000_000)
        var requests = 0
        val client = HttpClient(MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { request ->
                requests++
                if (request.url.host == "api.example") {
                    assertEquals("Bearer wallet-secret", request.headers[HttpHeaders.Authorization])
                    assertEquals("wallet=session", request.headers[HttpHeaders.Cookie])
                    respond("ok")
                } else {
                    assertNull(request.headers[HttpHeaders.Authorization]); assertNull(request.headers[HttpHeaders.Cookie])
                    assertEquals("no-cache", request.headers[HttpHeaders.CacheControl])
                    if (requests == 1) respond("list", headers = headersOf(
                        HttpHeaders.ContentType to listOf("application/statuslist+jwt"),
                        HttpHeaders.ETag to listOf("\"v1\""), HttpHeaders.CacheControl to listOf("max-age=60")))
                    else {
                        assertEquals("\"v1\"", request.headers[HttpHeaders.IfNoneMatch])
                        respond("", HttpStatusCode.NotModified, headersOf(HttpHeaders.CacheControl, "max-age=60"))
                    }
                }
            }
        })) { defaultRequest { header(HttpHeaders.Authorization, "Bearer wallet-secret"); header(HttpHeaders.Cookie, "wallet=session") } }
        val transport = StatusListTransport(client, backgroundScope, { at }, CredentialStatusLimits())
        try {
            val initial = transport.fetch("https://status.example/list", false)
            at += 1.seconds
            assertEquals(initial, transport.fetch("https://status.example/list", false))
            assertEquals(1, requests)
            val refreshed = transport.fetch("https://status.example/list", true)
            assertEquals(initial.content, refreshed.content)
            assertEquals(at, refreshed.retrievedAt)
            assertEquals(2, requests)
            transport.close()
            client.get("https://api.example/account")
            assertEquals(3, requests) // Closing/sanitizing the child did not alter the API client.
        } finally { transport.close(); client.close() }
    }

    @Test
    fun malformedTargetsRedirectsAndOversizedBodiesCannotReachOrPopulateTheCache() = runTest {
        var requests = 0
        val client = HttpClient(MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { request -> requests++
                when (request.url.encodedPath) {
                    "/redirect" -> respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://other.example/list"))
                    else -> respond("12345678901", headers = headersOf(HttpHeaders.ContentType, "application/statuslist+jwt"))
                }
            }
        })) { followRedirects = true }
        val transport = StatusListTransport(client, backgroundScope, { Instant.fromEpochSeconds(1_800_000_000) },
            CredentialStatusLimits(maxResponseBytes = 10))
        try {
            for (uri in listOf("http://status.example/list", "https://user:secret@status.example/list", "https://status.example/list#fragment")) {
                assertEquals(StatusFailure.REFERENCE_MALFORMED, assertFailsWith<CredentialStatusException> { transport.fetch(uri, true) }.reason)
            }
            assertEquals(0, requests)
            assertEquals(StatusFailure.TRANSPORT, assertFailsWith<CredentialStatusException> { transport.fetch("https://status.example/redirect", true) }.reason)
            assertEquals(1, requests)
            repeat(2) { assertEquals(StatusFailure.RESOURCE_LIMIT,
                assertFailsWith<CredentialStatusException> { transport.fetch("https://status.example/large", false) }.reason) }
            assertEquals(3, requests)
        } finally { transport.close(); client.close() }
    }

    @Test
    fun aTransportTimeoutIsTypedWhileCallerCancellationStaysCancellation() = runTest {
        val client = HttpClient(MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { awaitCancellation() }
        }))
        val transport = StatusListTransport(client, backgroundScope, { Instant.fromEpochSeconds(1_800_000_000) },
            CredentialStatusLimits(requestTimeout = 1.seconds))
        try {
            assertEquals(StatusFailure.TIMEOUT, assertFailsWith<CredentialStatusException> { transport.fetch("https://status.example/list", true) }.reason)
            val work = async { transport.fetch("https://status.example/list", true) }
            runCurrent(); work.cancel()
            assertFailsWith<CancellationException> { work.await() }
        } finally { transport.close(); client.close() }
    }
}
