package id.walt.wallet2.handlers

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

}
