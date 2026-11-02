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
        val contexts = (1..8).map { async { runtime.challengeContext("https://as.example", "client", "key") } }.awaitAll()
        contexts.forEach { assertSame(contexts.first(), it) }
        contexts.first().observe(headersOf("OAuth-Client-Attestation-Challenge", "opaque"))
        assertEquals("opaque", runtime.challengeContext("https://as.example", "client", "key").challenge)
        assertNull(runtime.challengeContext("https://other-as.example", "client", "key").challenge)
        assertNull(runtime.challengeContext("https://as.example", "other-client", "key").challenge)
        assertNull(runtime.challengeContext("https://as.example", "client", "rotated-key").challenge)
        assertNull(WalletIssuanceSessionState("other-wallet", null).challengeContext("https://as.example", "client", "key").challenge)
        assertNull(WalletIssuanceSessionState("wallet", null).challengeContext("https://as.example", "client", "key").challenge)
    }

    @Test fun storedFreshnessWinsOverAnOlderSessionSnapshotAndRemainsScoped() = runTest {
        val store = id.walt.wallet2.stores.inmemory.InMemoryIssuanceSessionStore()
        val first = WalletIssuanceSessionState("wallet", store)
        first.challengeContext("https://as.example", "client", "key")
            .observe(headersOf("OAuth-Client-Attestation-Challenge", "newest"))
        val reopened = WalletIssuanceSessionState("wallet", store)
        assertEquals("newest", reopened.challengeContext("https://as.example", "client", "key", "older").challenge)
        assertNull(reopened.challengeContext("https://other.example", "client", "key").challenge)
        assertNull(reopened.challengeContext("https://as.example", "other-client", "key").challenge)
        assertNull(reopened.challengeContext("https://as.example", "client", "new-key").challenge)
        // Existing stores with only per-session freshness still restore without a migration.
        assertEquals("legacy", reopened.challengeContext("https://legacy.example", "client", "key", "legacy").challenge)
        assertEquals("legacy", WalletIssuanceSessionState("wallet", store)
            .challengeContext("https://legacy.example", "client", "key").challenge)
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
}
