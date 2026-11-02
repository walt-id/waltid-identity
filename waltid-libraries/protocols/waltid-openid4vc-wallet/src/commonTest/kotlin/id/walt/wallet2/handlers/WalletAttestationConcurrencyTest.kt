package id.walt.wallet2.handlers

import id.walt.wallet2.stores.inmemory.InMemoryIssuanceSessionStore
import id.waltid.openid4vci.wallet.attestation.ClientAttestationHeaders
import id.waltid.openid4vci.wallet.oauth.ClientConfiguration
import id.waltid.openid4vci.wallet.token.TokenRequestBuilder
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Force independent wallet runtimes to race on the same persisted freshness snapshot. */
class WalletAttestationConcurrencyTest {
    private class ConcurrentReadStore : AtomicWalletIssuanceSessionCreationStore {
        private val delegate = InMemoryIssuanceSessionStore()
        var gateReads = false
        private var reads = 0
        private val ready = CompletableDeferred<Unit>()
        override suspend fun get(id: String): WalletIssuanceSessionRecord? {
            val value = delegate.get(id)
            if (gateReads) {
                if (++reads == 2) ready.complete(Unit)
                ready.await()
            }
            return value
        }
        override suspend fun list() = delegate.list()
        override suspend fun put(record: WalletIssuanceSessionRecord) = delegate.put(record)
        override suspend fun putIfAbsent(record: WalletIssuanceSessionRecord) = delegate.putIfAbsent(record)
        override suspend fun remove(id: String) = delegate.remove(id)
        override suspend fun compareAndSet(expected: WalletIssuanceSessionRecord, replacement: WalletIssuanceSessionRecord?) =
            delegate.compareAndSet(expected, replacement)
    }

    @Test fun concurrentResponsesDoNotFailWhenUpdatingExistingFreshness() = runTest {
        val store = ConcurrentReadStore()
        val first = WalletIssuanceSessionState("wallet", store).challengeContext("https://as.example", "client", "key")
        first.observe(headersOf("OAuth-Client-Attestation-Challenge", "initial"))
        val second = WalletIssuanceSessionState("wallet", store).challengeContext("https://as.example", "client", "key")
        store.gateReads = true
        val results = listOf(
            async { runCatching { first.observe(headersOf("OAuth-Client-Attestation-Challenge", "first-response")) } },
            async { runCatching { second.observe(headersOf("OAuth-Client-Attestation-Challenge", "second-response")) } },
        ).awaitAll()
        assertTrue(results.all { it.isSuccess }, "Response observers failed: ${results.map { it.exceptionOrNull() }}")
    }

    @Test fun concurrentResponsesRetainBothIndependentFreshnessValues() = runTest {
        for (existing in listOf(false, true)) {
            val store = ConcurrentReadStore()
            val first = WalletIssuanceSessionState("wallet", store).challengeContext("https://as.example", "client", "key")
            if (existing) first.observe(headersOf("OAuth-Client-Attestation-Challenge" to listOf("old-challenge"), "DPoP-Nonce" to listOf("old-nonce")))
            val second = WalletIssuanceSessionState("wallet", store).challengeContext("https://as.example", "client", "key")
            store.gateReads = true
            listOf(
                async { first.observe(headersOf("OAuth-Client-Attestation-Challenge", "challenge")) },
                async { second.observe(headersOf("DPoP-Nonce", "nonce")) },
            ).awaitAll()
            store.gateReads = false
            val restored = WalletIssuanceSessionState("wallet", store).challengeContext("https://as.example", "client", "key")
            assertEquals("challenge", restored.challenge)
            assertEquals("nonce", restored.dpopNonce)
            // Reloading either runtime must also expose the authoritative merged state.
            for (context in listOf(first, second)) {
                context.observe(Headers.Empty)
                assertEquals("challenge", context.challenge)
                assertEquals("nonce", context.dpopNonce)
            }
        }
    }

    @Test fun concurrentLegacyRestoreCannotReplaceLiveFreshness() = runTest {
        for (restoreFirst in listOf(false, true)) {
            val store = ConcurrentReadStore()
            val first = WalletIssuanceSessionState("wallet", store).challengeContext("https://as.example", "client", "key")
            val second = WalletIssuanceSessionState("wallet", store).challengeContext("https://as.example", "client", "key")
            store.gateReads = true
            val operations: List<suspend () -> Unit> = listOf(
                { first.restore("legacy-challenge", "legacy-nonce") },
                { second.observe(headersOf("OAuth-Client-Attestation-Challenge" to listOf("live-challenge"), "DPoP-Nonce" to listOf("live-nonce"))) },
            )
            (if (restoreFirst) operations else operations.reversed()).map { async { it() } }.awaitAll()
            store.gateReads = false
            val restored = WalletIssuanceSessionState("wallet", store).challengeContext("https://as.example", "client", "key")
            assertEquals("live-challenge", restored.challenge)
            assertEquals("live-nonce", restored.dpopNonce)
        }
    }

    @Test fun successfulTokenResponsesSurviveFreshnessContentionWithoutHttpRetries() = runTest {
        val store = ConcurrentReadStore()
        val first = WalletIssuanceSessionState("wallet", store).challengeContext("https://as.example", "client", "key")
        first.observe(headersOf("OAuth-Client-Attestation-Challenge", "initial"))
        val second = WalletIssuanceSessionState("wallet", store).challengeContext("https://as.example", "client", "key")
        var requests = 0
        val requestMutex = Mutex()
        val client = HttpClient(MockEngine) {
            engine { addHandler {
                val requestNumber = requestMutex.withLock { ++requests }
                respond("""{"access_token":"token-$requestNumber","token_type":"Bearer"}""",
                    HttpStatusCode.OK, headersOf("OAuth-Client-Attestation-Challenge", "response-$requestNumber"))
            } }
        }
        try {
            val builder = TokenRequestBuilder(
                ClientConfiguration("client", listOf("openid://callback")), client)
            store.gateReads = true
            val tokens = listOf(first, second).mapIndexed { index, context -> async {
                builder.exchangePreAuthorizedCode("https://as.example/token", "code-$index",
                    dpopProofFactory = null, onResponseHeaders = context::observe,
                    attestationHeadersFactory = { ClientAttestationHeaders("att", "proof-$index") })
            } }.awaitAll()
            assertEquals(setOf("token-1", "token-2"), tokens.map { it.access_token }.toSet())
            assertEquals(2, requests)
        } finally { client.close() }
    }
}
