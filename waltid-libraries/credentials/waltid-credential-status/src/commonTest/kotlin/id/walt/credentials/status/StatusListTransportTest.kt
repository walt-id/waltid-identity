@file:OptIn(ExperimentalCoroutinesApi::class)
package id.walt.credentials.status

import id.walt.policies2.vc.policies.status.StatusListContent
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.toHttpDate
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi

class StatusListTransportTest {
    @Test
    fun conditionalRevalidationRetainsExpiresUntilExplicitlyReplaced() = runTest {
        val start = Instant.parse("2026-11-17T12:00:00Z")
        var at = start
        var expires: Instant? = start + 60.seconds
        var cacheControl: String? = null
        var notModified = false
        var requests = 0
        val client = HttpClient(MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { request ->
                requests++
                if (requests > 1) assertEquals("\"v1\"", request.headers[HttpHeaders.IfNoneMatch])
                respond(if (notModified) "" else "list", if (notModified) HttpStatusCode.NotModified else HttpStatusCode.OK,
                    Headers.build {
                        append(HttpHeaders.ETag, "\"v1\"")
                        append(HttpHeaders.Date, GMTDate(at.toEpochMilliseconds()).toHttpDate())
                        expires?.let { append(HttpHeaders.Expires, GMTDate(it.toEpochMilliseconds()).toHttpDate()) }
                        cacheControl?.let { append(HttpHeaders.CacheControl, it) }
                    })
            }
        }))
        val transport = StatusListTransport(client, backgroundScope, { at }, CredentialStatusLimits())
        val request = StatusListRequest("https://status.example/list", StatusListRepresentation.TOKEN_JWT)
        try {
            val initial = transport.fetch(request, false)
            assertEquals(start + 60.seconds, initial.freshUntil)
            notModified = true
            expires = null
            at += 30.seconds
            val revalidated = transport.fetch(request, true)
            assertEquals(initial.content, revalidated.content)
            assertEquals(start + 60.seconds, revalidated.freshUntil, "Omitting Expires on 304 must retain the stored expiry")
            transport.fetch(request, false)
            assertEquals(2, requests, "The revalidated body remains reusable until its original deadline")
            at += 30.seconds
            assertEquals(at, transport.fetch(request, false).freshUntil)
            assertEquals(3, requests, "The original deadline must trigger another validation")
            expires = start + 120.seconds
            assertEquals(expires, transport.fetch(request, true).freshUntil, "A new Expires must replace the stored value")
            expires = null
            cacheControl = "max-age=10"
            at += 30.seconds
            assertEquals(at + 10.seconds, transport.fetch(request, true).freshUntil, "Max-age takes precedence over retained Expires")
            notModified = false
            cacheControl = null
            at += 10.seconds
            assertEquals(at + 15.minutes, transport.fetch(request, false).freshUntil, "A full response must not inherit old freshness headers")
            assertEquals(6, requests)
        } finally { transport.close(); client.close() }
    }

    @Test
    fun noCacheRevalidatesFreshObservationsAndStillHonorsExplicitZeroMaxAge() = runTest {
        var at = Instant.fromEpochSeconds(1_800_000_000)
        var cacheControl = "no-cache, max-age=60"
        var requests = 0
        val request = StatusListRequest("https://status.example/list", StatusListRepresentation.TOKEN_JWT)
        val client = HttpClient(MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { incoming ->
                requests++
                if (requests > 1) assertEquals("\"v1\"", incoming.headers[HttpHeaders.IfNoneMatch])
                respond(if (requests == 1) "list" else "", if (requests == 1) HttpStatusCode.OK else HttpStatusCode.NotModified,
                    headersOf(HttpHeaders.ContentType to listOf("application/statuslist+jwt"),
                        HttpHeaders.CacheControl to listOf(cacheControl), HttpHeaders.ETag to listOf("\"v1\"")))
            }
        }))
        val transport = StatusListTransport(client, backgroundScope, { at }, CredentialStatusLimits())
        try {
            val first = transport.fetch(request, false)
            assertEquals(at + 60.seconds, first.freshUntil)
            at += 1.seconds
            val revalidated = transport.fetch(request, false)
            assertEquals(2, requests, "no-cache requires validation even before the observation's freshness deadline")
            assertEquals(first.content, revalidated.content)
            assertEquals(at, revalidated.retrievedAt)
            assertEquals(at + 60.seconds, revalidated.freshUntil)
            cacheControl = "no-cache, max-age=0"
            assertEquals(at, transport.fetch(request, false).freshUntil)
            assertEquals(3, requests)
        } finally { transport.close(); client.close() }
    }

    @Test
    fun negotiatedVariantsSeparateFlightsBodiesValidatorsAndEviction() = runTest {
        val at = Instant.fromEpochSeconds(1_800_000_000)
        val jwt = StatusListRequest("https://status.example/list", StatusListRepresentation.TOKEN_JWT)
        val cwt = jwt.copy(representation = StatusListRepresentation.TOKEN_CWT)
        val release = CompletableDeferred<Unit>()
        val requests = mutableListOf<String>()
        val client = HttpClient(MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { request ->
                val accept = requireNotNull(request.headers[HttpHeaders.Accept])
                assertTrue(accept in listOf("application/statuslist+jwt", "application/statuslist+cwt"))
                requests += accept
                release.await()
                val etag = "\"$accept\""
                val conditional = request.headers[HttpHeaders.IfNoneMatch]
                if (conditional != null) assertEquals(etag, conditional)
                respond(if (conditional == null) accept else "", if (conditional == null) HttpStatusCode.OK else HttpStatusCode.NotModified,
                    headersOf(HttpHeaders.ContentType to listOf(accept), HttpHeaders.ETag to listOf(etag),
                        HttpHeaders.CacheControl to listOf("max-age=60"), HttpHeaders.Vary to listOf("Accept")))
            }
        }))
        val transport = StatusListTransport(client, backgroundScope, { at }, CredentialStatusLimits())
        try {
            val firstJwt = async { transport.fetch(jwt, false) }
            val secondJwt = async { transport.fetch(jwt, false) }
            val firstCwt = async { transport.fetch(cwt, false) }
            runCurrent()
            assertEquals(2, requests.size, "Only identical representations may share an in-flight request")
            release.complete(Unit)
            val jwtBody = firstJwt.await()
            val cwtBody = firstCwt.await()
            assertEquals(jwtBody, secondJwt.await())
            assertEquals("application/statuslist+jwt", assertIs<StatusListContent.Text>(jwtBody.content).content)
            assertEquals("application/statuslist+cwt", assertIs<StatusListContent.Binary>(cwtBody.content).content.decodeToString())
            assertEquals(jwtBody, transport.fetch(jwt, false))
            assertEquals(cwtBody, transport.fetch(cwt, false))
            assertEquals(2, requests.size)
            assertEquals(jwtBody, transport.fetch(jwt, true))
            assertEquals(cwtBody, transport.fetch(cwt, true))
            assertEquals(4, requests.size, "Each representation must revalidate its own ETag and body")
            transport.constrain(jwt, jwtBody.bodyId, at)
            assertEquals(cwtBody, transport.fetch(cwt, false))
            transport.fetch(jwt, false)
            assertEquals(5, requests.size)
            transport.invalidate(jwt, jwtBody.bodyId)
            assertEquals(cwtBody, transport.fetch(cwt, false))
            transport.fetch(jwt, false)
            assertEquals(6, requests.size, "Invalidating one representation must retain the other")
        } finally { release.complete(Unit); transport.close(); client.close() }
    }

    @Test
    fun noStoreBatchMemoizationKeepsRepresentationsSeparateAndEndsWithTheBatch() = runTest {
        val jwt = StatusListRequest("https://status.example/list", StatusListRepresentation.TOKEN_JWT)
        val cwt = jwt.copy(representation = StatusListRepresentation.TOKEN_CWT)
        var requests = 0
        val client = HttpClient(MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { request ->
                requests++
                val accept = requireNotNull(request.headers[HttpHeaders.Accept])
                respond(accept, headers = headersOf(HttpHeaders.ContentType to listOf(accept), HttpHeaders.CacheControl to listOf("no-store")))
            }
        }))
        val transport = StatusListTransport(client, backgroundScope, { Instant.fromEpochSeconds(1_800_000_000) }, CredentialStatusLimits())
        val session = transport.refreshSession()
        try {
            session.enter(transport)
            try {
                val jwtBody = transport.fetch(jwt, true, session)
                val cwtBody = transport.fetch(cwt, true, session)
                assertIs<StatusListContent.Text>(jwtBody.content)
                assertIs<StatusListContent.Binary>(cwtBody.content)
                assertEquals(jwtBody, transport.fetch(jwt, true, session))
                assertEquals(cwtBody, transport.fetch(cwt, true, session))
                assertEquals(2, requests)
            } finally { session.leave(); session.close() }
            transport.fetch(jwt, false)
            transport.fetch(cwt, false)
            assertEquals(4, requests, "No-store bodies may not outlive the batch")
        } finally { transport.close(); client.close() }
    }

    @Test
    fun responseLimitsRejectOpenOversizedBodiesBeforeWaitingForEof() = runTest {
        for (declared in listOf(true, false)) {
            val body = ByteChannel(autoFlush = true)
            if (!declared) body.writeFully("12345678901".encodeToByteArray())
            val client = HttpClient(MockEngine(MockEngineConfig().apply {
                dispatcher = StandardTestDispatcher(testScheduler)
                addHandler { respond(body, headers = if (declared) headersOf(HttpHeaders.ContentLength, "1000000") else headersOf()) }
            }))
            val transport = StatusListTransport(client, backgroundScope, { Instant.fromEpochSeconds(1_800_000_000) },
                CredentialStatusLimits(maxResponseBytes = 10, requestTimeout = 1.seconds))
            try {
                assertEquals(CheckFailure.Reason.RESOURCE_LIMIT,
                    assertFailsWith<CredentialStatusException> { transport.fetch(StatusListRequest("https://status.example/large", StatusListRepresentation.TOKEN_JWT), true) }.reason)
                assertTrue(body.isClosedForWrite, "Rejected response must release its open body")
            } finally { body.cancel(null); transport.close(); client.close() }
        }
        val body = ByteChannel(autoFlush = true)
        body.writeFully("1234567890".encodeToByteArray()); body.close()
        val client = HttpClient(MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { respond(body) }
        }))
        val transport = StatusListTransport(client, backgroundScope, { Instant.fromEpochSeconds(1_800_000_000) },
            CredentialStatusLimits(maxResponseBytes = 10))
        try {
            assertEquals(StatusListContent.Text("1234567890"), transport.fetch(StatusListRequest("https://status.example/exact", StatusListRepresentation.TOKEN_JWT), true).content)
        } finally { transport.close(); client.close() }
    }

    @Test
    fun backoffPrunesAbandonedUrlsAndPreservesOverflowDeadlinesWithoutGrowingTheTable() = runTest {
        var at = Instant.fromEpochSeconds(1_800_000_000)
        var requests = 0
        val client = HttpClient(MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler {
                requests++
                val wait = when (it.url.encodedPath) { "/a" -> 5; "/b" -> 20; else -> 30 }
                respond("", HttpStatusCode.TooManyRequests, headersOf(HttpHeaders.RetryAfter, wait.toString()))
            }
        }))
        val transport = StatusListTransport(client, backgroundScope, { at }, CredentialStatusLimits(maxCachedLists = 1))
        suspend fun limited(path: String) = assertFailsWith<CredentialStatusException> {
            transport.fetch(StatusListRequest("https://status.example/$path", StatusListRepresentation.TOKEN_JWT), true)
        }.reason
        try {
            assertEquals(CheckFailure.RateLimited(at + 5.seconds), limited("a"))
            assertEquals(CheckFailure.RateLimited(at + 5.seconds), assertFailsWith<CredentialStatusException> {
                transport.fetch(StatusListRequest("https://status.example/a", StatusListRepresentation.TOKEN_CWT), true)
            }.reason)
            assertEquals(1, requests, "Changing Accept must not bypass the endpoint's backoff")
            at += 6.seconds // Abandoned a expires before b needs the sole slot.
            assertEquals(CheckFailure.RateLimited(at + 20.seconds), limited("b"))
            assertEquals(CheckFailure.RateLimited(at + 30.seconds), limited("c"))
            assertEquals(3, requests)
            at += 21.seconds // b expired; c's overflow deadline must still apply to new work.
            assertEquals(CheckFailure.RateLimited(at + 9.seconds), limited("d"))
            assertEquals(3, requests)
            at += 10.seconds
            assertEquals(CheckFailure.RateLimited(at + 30.seconds), limited("d"))
            assertEquals(4, requests)
        } finally { transport.close(); client.close() }
    }

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
            val initial = transport.fetch(StatusListRequest("https://status.example/list", StatusListRepresentation.TOKEN_JWT), false)
            at += 1.seconds
            assertEquals(initial, transport.fetch(StatusListRequest("https://status.example/list", StatusListRepresentation.TOKEN_JWT), false))
            assertEquals(1, requests)
            val refreshed = transport.fetch(StatusListRequest("https://status.example/list", StatusListRepresentation.TOKEN_JWT), true)
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
                assertEquals(CheckFailure.Reason.INVALID_LIST, assertFailsWith<CredentialStatusException> { transport.fetch(StatusListRequest(uri, StatusListRepresentation.TOKEN_JWT), true) }.reason)
            }
            assertEquals(0, requests)
            assertEquals(CheckFailure.Reason.TRANSPORT, assertFailsWith<CredentialStatusException> { transport.fetch(StatusListRequest("https://status.example/redirect", StatusListRepresentation.TOKEN_JWT), true) }.reason)
            assertEquals(1, requests)
            repeat(2) { assertEquals(CheckFailure.Reason.RESOURCE_LIMIT,
                assertFailsWith<CredentialStatusException> { transport.fetch(StatusListRequest("https://status.example/large", StatusListRepresentation.TOKEN_JWT), false) }.reason) }
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
            assertEquals(CheckFailure.Reason.TIMEOUT, assertFailsWith<CredentialStatusException> { transport.fetch(StatusListRequest("https://status.example/list", StatusListRepresentation.TOKEN_JWT), true) }.reason)
            val work = async { transport.fetch(StatusListRequest("https://status.example/list", StatusListRepresentation.TOKEN_JWT), true) }
            runCurrent(); work.cancel()
            assertFailsWith<CancellationException> { work.await() }
        } finally { transport.close(); client.close() }
    }
}
