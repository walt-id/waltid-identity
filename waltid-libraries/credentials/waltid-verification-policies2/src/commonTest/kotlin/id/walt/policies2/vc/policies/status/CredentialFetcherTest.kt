package id.walt.policies2.vc.policies.status

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class CredentialFetcherTest {

    @Test
    fun `max-age caches the status list until it expires`() = runTest {
        var requests = 0
        var now = Instant.parse("2026-01-01T00:00:00Z")
        val fetcher = CredentialFetcher(
            client = countingClient(cacheControl = "public, max-age=60") { ++requests },
            now = { now },
        )

        val first = fetcher.fetch("https://example.com/status").getOrThrow()
        val second = fetcher.fetch("https://example.com/status").getOrThrow()
        now += 60.seconds
        val third = fetcher.fetch("https://example.com/status").getOrThrow()

        assertEquals(2, requests)
        assertEquals(StatusListContent.Text("token-1"), first)
        assertEquals(first, second)
        assertEquals(StatusListContent.Text("token-2"), third)
    }

    @Test
    fun `missing Cache-Control does not cache`() = runTest {
        var requests = 0
        val fetcher = CredentialFetcher(countingClient(cacheControl = null) { ++requests })
        fetcher.fetch("https://example.com/status").getOrThrow()
        fetcher.fetch("https://example.com/status").getOrThrow()
        assertEquals(2, requests)
    }

    @Test
    fun `no-store does not cache`() = runTest {
        var requests = 0
        val fetcher = CredentialFetcher(countingClient(cacheControl = "no-store") { ++requests })
        fetcher.fetch("https://example.com/status").getOrThrow()
        fetcher.fetch("https://example.com/status").getOrThrow()
        assertEquals(2, requests)
    }

    @Test
    fun `caches binary CWT content by content type`() = runTest {
        var requests = 0
        val bytes = byteArrayOf(0x01, 0x02, 0x03)
        val fetcher = CredentialFetcher(
            HttpClient(MockEngine) {
                engine {
                    addHandler {
                        requests++
                        respond(
                            content = bytes,
                            status = HttpStatusCode.OK,
                            headers = headersOf(
                                HttpHeaders.ContentType to listOf("application/statuslist+cwt"),
                                HttpHeaders.CacheControl to listOf("max-age=30"),
                            ),
                        )
                    }
                }
            },
        )
        val first = assertIs<StatusListContent.Binary>(fetcher.fetch("https://example.com/status.cwt").getOrThrow())
        val second = assertIs<StatusListContent.Binary>(fetcher.fetch("https://example.com/status.cwt").getOrThrow())
        assertEquals(1, requests)
        assertEquals(first, second)
    }

    @Test
    fun `parses public max-age and rejects no-cache`() {
        assertEquals(43_200L, HttpCacheControl.maxAgeSeconds("public, max-age=43200"))
        assertEquals(600L, HttpCacheControl.maxAgeSeconds("max-age=600, public"))
        assertEquals(null, HttpCacheControl.maxAgeSeconds(null))
        assertEquals(null, HttpCacheControl.maxAgeSeconds("max-age=0"))
        assertEquals(null, HttpCacheControl.maxAgeSeconds("no-store, max-age=60"))
        assertEquals(null, HttpCacheControl.maxAgeSeconds("no-cache"))
    }

    private fun countingClient(
        cacheControl: String?,
        nextRequest: () -> Int,
    ) = HttpClient(MockEngine) {
        engine {
            addHandler {
                val n = nextRequest()
                val headers = buildList {
                    add(HttpHeaders.ContentType to listOf("application/statuslist+jwt"))
                    cacheControl?.let { add(HttpHeaders.CacheControl to listOf(it)) }
                }
                respond(
                    content = "token-$n",
                    status = HttpStatusCode.OK,
                    headers = headersOf(*headers.toTypedArray()),
                )
            }
        }
    }
}
