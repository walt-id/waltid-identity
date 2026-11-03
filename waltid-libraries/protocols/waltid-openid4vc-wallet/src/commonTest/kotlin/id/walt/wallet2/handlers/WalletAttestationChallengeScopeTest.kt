package id.walt.wallet2.handlers

import io.ktor.client.engine.mock.respond
import io.ktor.http.headersOf
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class WalletAttestationChallengeScopeTest {
    @Test fun sharedRuntimeRetainsFreshnessOnlyForTheSameClientInstanceAndServer() = runTest {
        val runtime = WalletIssuanceSessionState("wallet", null)
        val contexts = (1..8).map { async { runtime.challengeContext("https://as.example", "client", "key", flowId = "test-flow") } }.awaitAll()
        contexts.forEach { assertSame(contexts.first(), it) }
        contexts.first().observe(headersOf("OAuth-Client-Attestation-Challenge", "opaque"))
        assertEquals("opaque", runtime.challengeContext("https://as.example", "client", "key", flowId = "test-flow").challenge)
        assertNull(runtime.challengeContext("https://other-as.example", "client", "key", flowId = "test-flow").challenge)
        assertNull(runtime.challengeContext("https://as.example", "other-client", "key", flowId = "test-flow").challenge)
        assertNull(runtime.challengeContext("https://as.example", "client", "rotated-key", flowId = "test-flow").challenge)
        assertNull(WalletIssuanceSessionState("other-wallet", null).challengeContext("https://as.example", "client", "key", flowId = "test-flow").challenge)
        assertNull(WalletIssuanceSessionState("wallet", null).challengeContext("https://as.example", "client", "key", flowId = "test-flow").challenge)
    }

    @Test fun storedFreshnessWinsOverAnOlderSessionSnapshotAndRemainsScoped() = runTest {
        val store = id.walt.wallet2.stores.inmemory.InMemoryIssuanceSessionStore()
        val first = WalletIssuanceSessionState("wallet", store)
        first.challengeContext("https://as.example", "client", "key", flowId = "test-flow")
            .observe(headersOf("OAuth-Client-Attestation-Challenge", "newest"))
        val reopened = WalletIssuanceSessionState("wallet", store)
        assertEquals("newest", reopened.challengeContext("https://as.example", "client", "key", "older", flowId = "test-flow").challenge)
        assertNull(reopened.challengeContext("https://other.example", "client", "key", flowId = "test-flow").challenge)
        assertNull(reopened.challengeContext("https://as.example", "other-client", "key", flowId = "test-flow").challenge)
        assertNull(reopened.challengeContext("https://as.example", "client", "new-key", flowId = "test-flow").challenge)
        // Existing stores with only per-session freshness still restore without a migration.
        assertEquals("legacy", reopened.challengeContext("https://legacy.example", "client", "key", "legacy", flowId = "test-flow").challenge)
        assertEquals("legacy", WalletIssuanceSessionState("wallet", store)
            .challengeContext("https://legacy.example", "client", "key", flowId = "test-flow").challenge)
    }

    @Test fun newFlowsFetchWhileReopenedFlowsKeepResponseChallenges() = runTest {
        val store = id.walt.wallet2.stores.inmemory.InMemoryIssuanceSessionStore()
        var fetches = 0
        val http = io.ktor.client.HttpClient(io.ktor.client.engine.mock.MockEngine) {
            engine { addHandler {
                fetches++
                respond("""{"attestation_challenge":"fetch-$fetches"}""")
            } }
        }
        try {
            val runtime = WalletIssuanceSessionState("wallet", store)
            val first = runtime.challengeContext("https://as.example", "client", "key", "old", flowId = "first")
            first.prepare(http, "https://as.example/challenge")
            assertEquals("fetch-1", first.challenge)
            first.observe(headersOf("OAuth-Client-Attestation-Challenge", "par-response"))
            val second = runtime.challengeContext("https://as.example", "client", "key", flowId = "second")
            second.prepare(http, "https://as.example/challenge")
            assertEquals("fetch-2", second.challenge)
            val reopened = WalletIssuanceSessionState("wallet", store)
                .challengeContext("https://as.example", "client", "key", "stale", flowId = "first")
            reopened.prepare(http, "https://as.example/challenge")
            assertEquals("par-response", reopened.challenge)
            assertEquals(2, fetches)
        } finally { http.close() }
    }

    @Test fun expiredLegacyCorrelationFetchesAgainWithoutExpiringRetainedSessions() = runTest {
        val store = id.walt.wallet2.stores.inmemory.InMemoryIssuanceSessionStore()
        var fetches = 0
        val http = io.ktor.client.HttpClient(io.ktor.client.engine.mock.MockEngine) {
            engine { addHandler {
                fetches++
                respond("""{"attestation_challenge":"fetch-$fetches"}""")
            } }
        }
        try {
            val runtime = WalletIssuanceSessionState("wallet", store)
            for (flow in listOf("pkce:correlation", "retained-session")) {
                runtime.challengeContext("https://as.example", "client", "key", flowId = flow)
                    .prepare(http, "https://as.example/challenge")
            }
            store.list().forEach { store.put(it.copy(updatedAtEpochMilliseconds = 0)) }
            val reopened = WalletIssuanceSessionState("wallet", store)
            reopened.challengeContext("https://as.example", "client", "key", flowId = "retained-session")
                .prepare(http, "https://as.example/challenge")
            assertEquals(2, fetches, "Retained flows use their session lifecycle")
            reopened.challengeContext("https://as.example", "client", "key", flowId = "pkce:correlation")
                .prepare(http, "https://as.example/challenge")
            assertEquals(3, fetches, "Expired legacy correlation must prepare again")
        } finally { http.close() }
    }

    @Test fun malformedRecordsDoNotBlockThrottledSweepsOrCompletion() = runTest {
        val backing = id.walt.wallet2.stores.inmemory.InMemoryIssuanceSessionStore()
        var lists = 0
        val store = object : AtomicWalletIssuanceSessionCreationStore by backing {
            override suspend fun list(): List<WalletIssuanceSessionRecord> { lists++; return backing.list() }
        }
        val broken = WalletIssuanceSessionRecord("attestation-flow:broken", "attestation-flow:broken",
            WalletIssuanceSessionRecordKind.CLIENT_ATTESTATION_FRESHNESS, "not-json", 0)
        backing.put(broken)
        val runtime = WalletIssuanceSessionState("wallet", store)
        var now = kotlin.time.Clock.System.now().toEpochMilliseconds()
        runtime.challengeNow = { now }
        runtime.challengeContext("https://as.example", "client", "key", flowId = "flow")
            .observe(headersOf("OAuth-Client-Attestation-Challenge", "fresh"))
        repeat(3) { runtime.challengeContext("https://as.example", "client", "key", flowId = "flow") }
        assertEquals(1, lists)
        runtime.challengeContext("https://as.example", "client", "key", flowId = "pkce:orphan")
            .observe(headersOf("OAuth-Client-Attestation-Challenge", "old"))
        backing.list().filter { it.payload.contains("pkce:orphan") }.forEach {
            backing.put(it.copy(updatedAtEpochMilliseconds = 0))
        }
        now += 60_000
        runtime.challengeContext("https://as.example", "client", "key", flowId = "flow")
        assertEquals(2, lists)
        runtime.removeChallengeFlow("flow")
        assertEquals(listOf(broken), backing.list())
    }

    @Test fun requestedPkceExpiryIsEnforcedBetweenSweeps() = runTest {
        val store = id.walt.wallet2.stores.inmemory.InMemoryIssuanceSessionStore()
        val runtime = WalletIssuanceSessionState("wallet", store)
        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
        runtime.challengeNow = { now }
        runtime.challengeContext("https://as.example", "client", "key", flowId = "pkce:flow")
            .observe(headersOf("OAuth-Client-Attestation-Challenge", "expired"))
        store.list().forEach { store.put(it.copy(updatedAtEpochMilliseconds = now - 600_001)) }
        val reopened = runtime.challengeContext("https://as.example", "client", "key", flowId = "pkce:flow")
        assertNull(reopened.challenge)
        assertTrue(store.list().isEmpty())
    }

    @Test fun cleanupDoesNotDeleteAConcurrentUpdate() = runTest {
        val backing = id.walt.wallet2.stores.inmemory.InMemoryIssuanceSessionStore()
        var race = false
        val store = object : AtomicWalletIssuanceSessionCreationStore by backing {
            override suspend fun compareAndSet(expected: WalletIssuanceSessionRecord, replacement: WalletIssuanceSessionRecord?): Boolean {
                if (race && replacement == null) {
                    race = false
                    backing.put(expected.copy(updatedAtEpochMilliseconds = expected.updatedAtEpochMilliseconds + 1))
                }
                return backing.compareAndSet(expected, replacement)
            }
        }
        val runtime = WalletIssuanceSessionState("wallet", store)
        runtime.challengeContext("https://as.example", "client", "key", flowId = "flow")
            .observe(headersOf("OAuth-Client-Attestation-Challenge", "fresh"))
        race = true
        runtime.removeChallengeFlow("flow")
        assertEquals(1, backing.list().size)
    }

    @Test fun sweepStorageCancellationPropagates() = runTest {
        val backing = id.walt.wallet2.stores.inmemory.InMemoryIssuanceSessionStore()
        val store = object : WalletIssuanceSessionStore by backing {
            override suspend fun list(): List<WalletIssuanceSessionRecord> = throw kotlinx.coroutines.CancellationException("cancel")
        }
        assertFailsWith<kotlinx.coroutines.CancellationException> {
            WalletIssuanceSessionState("wallet", store).challengeContext("https://as.example", "client", "key", flowId = "flow")
        }
    }
}
