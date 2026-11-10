package id.walt.openid4vci.attestation

import id.walt.webdatafetching.WebDataFetcher
import id.walt.webdatafetching.WebDataFetcherId
import id.walt.webdatafetching.WebDataFetchingConfiguration
import id.walt.webdatafetching.config.AllowList
import id.walt.webdatafetching.config.CacheConfiguration
import id.walt.webdatafetching.config.RequestConfiguration
import id.walt.webdatafetching.config.RequestEngineConfiguration
import id.walt.webdatafetching.config.url.UrlConfiguration
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.minutes

class HttpAttestationStatusResolverTest {
    private val uri = "https://attester.example/status/1"
    private val mediaType = headersOf(HttpHeaders.ContentType, "application/statuslist+jwt")
    private val defaults = WebDataFetchingConfiguration(maxResponseBytes = HttpAttestationStatusResolver.MAXIMUM_RESPONSE_BYTES)

    private fun TestScope.engine(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): MockEngine =
        MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler(handler)
        })

    private fun fetcher(engine: MockEngine, configuration: WebDataFetchingConfiguration = defaults): WebDataFetcher =
        WebDataFetcher.wrapping(HttpClient(engine) { configuration.applyConfigurationToHttpClient(this) },
            WebDataFetcherId.OPENID4VCI_ATTESTATION_STATUS.name, configuration)

    @Test fun everyCheckpointFetchesAgainDespiteConfiguredCacheAndHttpMaxAge() = runTest {
        var requests = 0
        val fetcher = fetcher(engine { request ->
            assertEquals("application/statuslist+jwt", request.headers[HttpHeaders.Accept])
            assertEquals("no-cache", request.headers[HttpHeaders.CacheControl])
            respond(if (++requests == 1) "valid-jwt" else "invalid-jwt", headers = headersOf(
                HttpHeaders.ContentType to listOf("application/statuslist+jwt"),
                HttpHeaders.CacheControl to listOf("max-age=3600")))
        }, defaults.copy(cache = CacheConfiguration(null, 10.minutes, 10)))
        try {
            val resolver = HttpAttestationStatusResolver(fetcher)
            assertEquals("valid-jwt", resolver.resolve(uri))
            assertEquals("invalid-jwt", resolver.resolve(uri))
            assertEquals(2, requests)
        } finally { fetcher.close() }
    }

    @Test fun genericRequestCredentialsAndMethodAreNotForwarded() = runTest {
        val configuration = defaults.copy(request = RequestConfiguration(
            method = id.walt.webdatafetching.config.http.HttpMethod.Post,
            headers = mapOf("Authorization" to "Bearer inherited", "X-API-Key" to "inherited", "Accept" to "application/json"),
            cookies = mapOf("session" to "inherited"),
            auth = RequestConfiguration.HttpAuthConfiguration.BasicAuth("user", "password"),
        ))
        val fetcher = fetcher(engine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertNull(request.headers[HttpHeaders.Authorization])
            assertNull(request.headers[HttpHeaders.Cookie])
            assertNull(request.headers["X-API-Key"])
            assertEquals(listOf("application/statuslist+jwt"), request.headers.getAll(HttpHeaders.Accept))
            respond("jwt", headers = mediaType)
        }, configuration)
        try { assertEquals("jwt", HttpAttestationStatusResolver(fetcher).resolve(uri)) }
        finally { fetcher.close() }
    }

    @Test fun aTemporaryFailureDoesNotPoisonTheNextLookup() = runTest {
        var requests = 0
        val fetcher = fetcher(engine {
            if (++requests == 1) respond("unavailable", HttpStatusCode.ServiceUnavailable, mediaType)
            else respond("jwt", headers = mediaType)
        })
        try {
            val resolver = HttpAttestationStatusResolver(fetcher)
            assertFailsWith<IllegalArgumentException> { resolver.resolve(uri) }
            assertEquals("jwt", resolver.resolve(uri))
        } finally { fetcher.close() }
    }

    @Test fun refusesRedirectsWithoutContactingTheTarget() = runTest {
        var requests = 0
        val fetcher = fetcher(engine {
            requests++
            respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://other.example/status"))
        })
        try {
            assertFailsWith<IllegalArgumentException> { HttpAttestationStatusResolver(fetcher).resolve(uri) }
            assertEquals(1, requests)
        } finally { fetcher.close() }
    }

    @Test fun responseMustHaveTheStatusListMediaType() = runTest {
        val fetcher = fetcher(engine { respond("jwt", headers = headersOf(HttpHeaders.ContentType, "application/jwt")) })
        try { assertFailsWith<IllegalArgumentException> { HttpAttestationStatusResolver(fetcher).resolve(uri) } }
        finally { fetcher.close() }
    }

    @Test fun acceptsMediaTypeCaseAndParameters() = runTest {
        val fetcher = fetcher(engine { respond(" jwt\n", headers = headersOf(HttpHeaders.ContentType, "Application/StatusList+JWT; charset=UTF-8")) })
        try { assertEquals("jwt", HttpAttestationStatusResolver(fetcher).resolve(uri)) }
        finally { fetcher.close() }
    }

    @Test fun rejectsOversizedDeclaredAndStreamedResponses() = runTest {
        for (declared in listOf(true, false)) {
            val fetcher = fetcher(engine {
                respond(ByteReadChannel(ByteArray(129) { 'a'.code.toByte() }), headers = Headers.build {
                    append(HttpHeaders.ContentType, "application/statuslist+jwt")
                    if (declared) append(HttpHeaders.ContentLength, "129")
                })
            }, defaults.copy(maxResponseBytes = 128))
            try { assertFails { HttpAttestationStatusResolver(fetcher).resolve(uri) } }
            finally { fetcher.close() }
        }
    }

    @Test fun transportAndSharedUrlRestrictionsRunBeforeAnyNetworkCall() = runTest {
        var requests = 0
        val fetcher = fetcher(engine { requests++; respond("jwt", headers = mediaType) }, defaults.copy(
            url = UrlConfiguration(hosts = AllowList(whitelist = listOf("attester.example")))))
        try {
            val resolver = HttpAttestationStatusResolver(fetcher)
            for (url in listOf("http://attester.example/status", "https://user:password@attester.example/status", "https://other.example/status")) {
                assertFailsWith<IllegalArgumentException>(url) { resolver.resolve(url) }
            }
            assertEquals(0, requests)
        } finally { fetcher.close() }
    }

    @Test fun rejectsConfigurationThatWeakensResponseOrRedirectLimits() = runTest {
        for (configuration in listOf(defaults.copy(maxResponseBytes = 0), defaults.copy(maxResponseBytes = -1),
            defaults.copy(maxResponseBytes = HttpAttestationStatusResolver.MAXIMUM_RESPONSE_BYTES + 1),
            defaults.copy(engine = RequestEngineConfiguration(followRedirects = true)))) {
            val fetcher = fetcher(engine { respond("jwt", headers = mediaType) }, configuration)
            try { assertFailsWith<IllegalArgumentException> { HttpAttestationStatusResolver(fetcher) } }
            finally { fetcher.close() }
        }
    }

    @Test fun totalLookupTimeoutIsRetryableAndCancelsTheRequest() = runTest {
        val requestCancelled = CompletableDeferred<Unit>()
        val engine = engine {
            try { awaitCancellation() }
            finally { requestCancelled.complete(Unit) }
        }
        val fetcher = fetcher(engine)
        try {
            val failure = assertFailsWith<IllegalStateException> { HttpAttestationStatusResolver(fetcher).resolve(uri) }
            assertEquals("Status-list lookup timed out", failure.message)
            requestCancelled.await()
        } finally { fetcher.close() }
    }

    @Test fun callerCancellationPropagatesAndCancelsTheRequest() = runTest {
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val engine = engine {
            started.complete(Unit)
            try { awaitCancellation() }
            finally { cancelled.complete(Unit) }
        }
        val fetcher = fetcher(engine)
        try {
            val lookup = async { HttpAttestationStatusResolver(fetcher).resolve(uri) }
            started.await()
            lookup.cancel()
            assertFailsWith<CancellationException> { lookup.await() }
            cancelled.await()
        } finally { fetcher.close() }
    }
}
