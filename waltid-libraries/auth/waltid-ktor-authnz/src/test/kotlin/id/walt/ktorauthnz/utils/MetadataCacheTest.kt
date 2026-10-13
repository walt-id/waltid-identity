package id.walt.ktorauthnz.utils

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class MetadataCacheTest {

    private var now = Instant.parse("2026-10-05T12:00:00Z")
    private val clock = object : Clock { override fun now() = this@MetadataCacheTest.now }
    private val cache = MetadataCache<String, String>("test", lifetime = 1.hours, minRefreshInterval = 1.minutes, staleIfError = 24.hours, clock = clock)
    private val fetches = AtomicInteger()
    private suspend fun fetch() = "v${fetches.incrementAndGet()}"

    @Test
    fun `a value is kept for its lifetime, then fetched again`() = runTest {
        assertEquals("v1", cache.get("k", ::fetch))
        now += 59.minutes
        assertEquals("v1", cache.get("k", ::fetch))
        now += 2.minutes
        assertEquals("v2", cache.get("k", ::fetch))
    }

    @Test
    fun `concurrent requests share one fetch`() = runTest {
        val release = CompletableDeferred<Unit>()
        val results = List(20) { async { cache.get("k") { release.await(); fetch() } } }
        testScheduler.runCurrent() // all 20 are waiting, one of them in the fetch
        release.complete(Unit)
        assertEquals(List(20) { "v1" }, results.awaitAll())
        assertEquals(1, fetches.get())
    }

    @Test
    fun `refreshing is limited to once per interval`() = runTest {
        cache.get("k", ::fetch)
        now += 30.seconds
        repeat(10) { assertEquals("v1", cache.refresh("k", ::fetch)) }
        now += 31.seconds
        assertEquals("v2", cache.refresh("k", ::fetch))
        assertEquals(2, fetches.get())
    }

    @Test
    fun `the last value is used while the server fails, for a while`() = runTest {
        cache.get("k", ::fetch)
        val failing: suspend () -> String = { error("server down") }
        now += 2.hours
        assertEquals("v1", cache.get("k", failing))
        now += 24.hours
        assertFailsWith<IllegalStateException> { cache.get("k", failing) }
    }

    @Test
    fun `a failure without a previous value is not hidden`() = runTest {
        assertFailsWith<IllegalStateException> { cache.get("k") { error("server down") } }
    }
}
