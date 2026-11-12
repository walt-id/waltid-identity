package id.walt.policies2.vc.policies.status

import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.CborLabel
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class CredentialFetcherTest {

    @Test
    fun `max-age caches the status list until it expires`() = runTest {
        var requests = 0
        var now = Instant.parse("2026-01-01T00:00:00Z")
        val fetcher = CredentialFetcher(
            client = countingClient { ++requests },
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
        val fetcher = CredentialFetcher(countingClient(cacheControl = emptyList()) { ++requests })
        fetcher.fetch("https://example.com/status").getOrThrow()
        fetcher.fetch("https://example.com/status").getOrThrow()
        assertEquals(2, requests)
    }

    @Test
    fun `token ttl does not cache when Cache-Control is missing`() = runTest {
        var requests = 0
        val fetcher = CredentialFetcher(
            countingClient(
                cacheControl = emptyList(),
                body = { unsignedJwt(ttl = 43_200, n = it) },
            ) { ++requests },
        )
        fetcher.fetch("https://example.com/status").getOrThrow()
        fetcher.fetch("https://example.com/status").getOrThrow()
        assertEquals(2, requests)
    }

    @Test
    fun `no-store does not cache`() = runTest {
        var requests = 0
        val fetcher = CredentialFetcher(countingClient(cacheControl = listOf("no-store")) { ++requests })
        fetcher.fetch("https://example.com/status").getOrThrow()
        fetcher.fetch("https://example.com/status").getOrThrow()
        assertEquals(2, requests)
    }

    @Test
    fun `token ttl does not cache under no-store`() = runTest {
        var requests = 0
        val fetcher = CredentialFetcher(
            countingClient(
                cacheControl = listOf("no-store"),
                body = { unsignedJwt(ttl = 43_200, n = it) },
            ) { ++requests },
        )
        fetcher.fetch("https://example.com/status").getOrThrow()
        fetcher.fetch("https://example.com/status").getOrThrow()
        assertEquals(2, requests)
    }

    @Test
    fun `token ttl does not cache when Age exhausts max-age`() = runTest {
        var requests = 0
        val fetcher = CredentialFetcher(
            countingClient(
                cacheControl = listOf("max-age=60"),
                age = "60",
                body = { unsignedJwt(ttl = 43_200, n = it) },
            ) { ++requests },
        )
        fetcher.fetch("https://example.com/status").getOrThrow()
        fetcher.fetch("https://example.com/status").getOrThrow()
        assertEquals(2, requests)
    }

    @Test
    fun `later Cache-Control no-store field is honored`() = runTest {
        var requests = 0
        val fetcher = CredentialFetcher(
            countingClient(cacheControl = listOf("max-age=600", "no-store")) { ++requests },
        )
        fetcher.fetch("https://example.com/status").getOrThrow()
        fetcher.fetch("https://example.com/status").getOrThrow()
        assertEquals(2, requests)
    }

    @Test
    fun `Age shortens remaining HTTP freshness`() = runTest {
        var requests = 0
        var now = Instant.parse("2026-01-01T00:00:00Z")
        val fetcher = CredentialFetcher(
            client = countingClient(cacheControl = listOf("max-age=60"), age = "59") { ++requests },
            now = { now },
        )
        fetcher.fetch("https://example.com/status").getOrThrow()
        fetcher.fetch("https://example.com/status").getOrThrow()
        assertEquals(1, requests)
        now += 1.seconds
        fetcher.fetch("https://example.com/status").getOrThrow()
        assertEquals(2, requests)
    }

    @Test
    fun `token ttl bounds reuse below HTTP max-age`() = runTest {
        var requests = 0
        var now = Instant.parse("2026-01-01T00:00:00Z")
        val fetcher = CredentialFetcher(
            client = countingClient(
                cacheControl = listOf("max-age=600"),
                body = { unsignedJwt(ttl = 1, n = it) },
            ) { ++requests },
            now = { now },
        )
        fetcher.fetch("https://example.com/status").getOrThrow()
        fetcher.fetch("https://example.com/status").getOrThrow()
        assertEquals(1, requests)
        now += 1.seconds
        fetcher.fetch("https://example.com/status").getOrThrow()
        assertEquals(2, requests)
    }

    @Test
    fun `token exp bounds reuse below HTTP max-age`() = runTest {
        var requests = 0
        var now = Instant.parse("2026-01-01T00:00:00Z")
        val fetcher = CredentialFetcher(
            client = countingClient(
                cacheControl = listOf("max-age=600"),
                body = { unsignedJwt(exp = now.epochSeconds + 5, n = it) },
            ) { ++requests },
            now = { now },
        )
        fetcher.fetch("https://example.com/status").getOrThrow()
        fetcher.fetch("https://example.com/status").getOrThrow()
        assertEquals(1, requests)
        now += 5.seconds
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
    fun `one-argument constructor remains callable`() {
        val client = countingClient { 1 }
        assertNotNull(CredentialFetcher(client))
    }

    @Test
    fun `parses public max-age and rejects no-cache`() {
        assertEquals(43_200L, HttpCacheControl.maxAgeSeconds("public, max-age=43200"))
        assertEquals(600L, HttpCacheControl.maxAgeSeconds("max-age=600, public"))
        assertEquals(null, HttpCacheControl.maxAgeSeconds(null))
        assertEquals(null, HttpCacheControl.maxAgeSeconds("max-age=0"))
        assertEquals(null, HttpCacheControl.maxAgeSeconds("no-store, max-age=60"))
        assertEquals(null, HttpCacheControl.maxAgeSeconds("no-cache"))
        assertEquals(
            null,
            HttpCacheControl.remainingFreshness(listOf("max-age=600", "no-cache"), ageSeconds = null, responseDelay = Duration.ZERO),
        )
        assertEquals(
            1.seconds,
            HttpCacheControl.remainingFreshness(listOf("max-age=60"), ageSeconds = 59, responseDelay = Duration.ZERO),
        )
        val now = Instant.parse("2026-01-01T00:00:00Z")
        assertEquals(
            null,
            StatusListCacheExpiry.of(now, httpRemaining = null, ttlSeconds = 43_200, expEpochSeconds = now.epochSeconds + 60),
        )
        assertEquals(
            now + 1.seconds,
            StatusListCacheExpiry.of(now, httpRemaining = 60.seconds, ttlSeconds = 1, expEpochSeconds = null),
        )
    }

    @Test
    fun `reads ttl and exp from a JWT payload`() {
        val jwt = unsignedJwt(ttl = 1, exp = 1_700_000_000, n = 1)
        assertEquals(1L to 1_700_000_000L, StatusListTokenLifetime.fromJwt(jwt))
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `reads ttl and exp from a CWT payload`() {
        val payload = id.walt.cose.coseCompliantCbor.encodeToByteArray(CwtLifetimeClaims(exp = 1_700_000_000, ttl = 1))
        assertEquals(1L to 1_700_000_000L, StatusListTokenLifetime.fromCwtPayload(payload))
    }

    private fun countingClient(
        cacheControl: List<String> = listOf("public, max-age=60"),
        age: String? = null,
        body: (Int) -> String = { "token-$it" },
        nextRequest: () -> Int,
    ) = HttpClient(MockEngine) {
        engine {
            addHandler {
                val n = nextRequest()
                val headers = buildList {
                    add(HttpHeaders.ContentType to listOf("application/statuslist+jwt"))
                    if (cacheControl.isNotEmpty()) add(HttpHeaders.CacheControl to cacheControl)
                    age?.let { add(HttpHeaders.Age to listOf(it)) }
                }
                respond(
                    content = body(n),
                    status = HttpStatusCode.OK,
                    headers = headersOf(*headers.toTypedArray()),
                )
            }
        }
    }

    private fun unsignedJwt(ttl: Long? = null, exp: Long? = null, n: Int): String {
        val payload = buildJsonObject {
            ttl?.let { put("ttl", it) }
            exp?.let { put("exp", it) }
            put("n", n)
        }
        val payloadB64 = Json.encodeToString(payload).encodeToByteArray().encodeToBase64Url()
        return "eyJhbGciOiJub25lIn0.$payloadB64.sig"
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
private data class CwtLifetimeClaims(
    @CborLabel(4) val exp: Long? = null,
    @CborLabel(65534) val ttl: Long? = null,
)
