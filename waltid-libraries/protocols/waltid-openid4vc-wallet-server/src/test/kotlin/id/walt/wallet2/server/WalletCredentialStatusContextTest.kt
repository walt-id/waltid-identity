package id.walt.wallet2.server

import id.walt.credentials.status.CredentialStatusIssue
import id.walt.credentials.status.CredentialStatusKind
import id.walt.credentials.status.StatusTestIssuer
import id.walt.errors.StatusException
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.status.CredentialStatusRefreshMode
import id.walt.wallet2.status.CredentialStatusReviewChangedException
import id.walt.wallet2.status.RefreshCredentialStatusesRequest
import id.walt.wallet2.status.WalletCredentialReference
import id.walt.wallet2.status.WalletCredentialStatusService
import id.walt.wallet2.stores.inmemory.InMemoryCredentialStore
import id.waltid.openid4vp.wallet.presentation.PresentationReviewGuard
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class WalletCredentialStatusContextTest {
    @Test
    fun signedMixedBatchSurvivesPollingAndReviewWithoutPersistingRestrictedEvidence() = runTest {
        val persistent = StatusTestIssuer.create("https://status.example/cacheable")
        val transient = StatusTestIssuer.create("https://status.example/no-store")
        val store = InMemoryCredentialStore().apply {
            addCredential(StoredCredential("cached", persistent.credential()))
            addCredential(StoredCredential("transient", transient.credential()))
        }
        val wallet = Wallet("wallet", credentialStores = listOf(store))
        var at = Instant.fromEpochSeconds(1_800_000_000)
        var failure = false
        val issuerRequests = AtomicInteger()
        var authorizedServices = 0
        val cachedBody = persistent.list(ttl = 600)
        var transientBody = transient.list(1u, ttl = 600)
        val client = HttpClient(MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { request ->
                issuerRequests.incrementAndGet()
                if (failure) respond("temporarily unavailable", HttpStatusCode.ServiceUnavailable)
                else respond(if (request.url.toString() == transient.uri) transientBody else cachedBody, headers = Headers.build {
                    append(HttpHeaders.ContentType, "application/statuslist+jwt")
                    append(HttpHeaders.CacheControl, if (request.url.toString() == transient.uri) "no-store" else "max-age=600")
                })
            }
        }))
        val requests = WalletCredentialStatusRequests(now = { at }) {
            authorizedServices++
            WalletCredentialStatusService(it, client, now = { at }, trustPolicyId = "test-v1", parentScope = backgroundScope)
        }
        val scope = WalletCredentialStatusRequests.Scope(Any(), wallet.id)
        try {
            val context = assertNotNull(requests.createContext(wallet, scope).statusContext).id
            val refreshed = requests.refresh(wallet, scope, RefreshCredentialStatusesRequest(mode = CredentialStatusRefreshMode.FORCE, statusContext = context))
            assertEquals(2, issuerRequests.get())
            assertEquals(CredentialStatusIssue.INVALID, refreshed.single { it.reference.credentialId == "transient" }.presentation.issue)
            assertTrue(refreshed.all { it.statusContext?.id == context })
            assertNotNull(store.readCredentialStatus("cached"))
            assertNull(store.readCredentialStatus("transient"))
            val cold = requests.snapshot(wallet, scope)
            assertEquals(CredentialStatusKind.NOT_CHECKED, cold.credentials.single { it.reference.credentialId == "transient" }.presentation.kind)
            val polled = requests.snapshot(wallet, scope, context)
            assertEquals(CredentialStatusIssue.INVALID, polled.credentials.single { it.reference.credentialId == "transient" }.presentation.issue)
            val reviewed = polled.credentials.associate { it.reference.credentialId to it.presentation.advisoryRevision }
            requests.withReviewedStatus(wallet, scope, reviewed.keys, reviewed, context) {
                currentCoroutineContext()[PresentationReviewGuard]!!.check()
            }
            assertEquals(2, issuerRequests.get(), "Reads and every guard boundary must remain local")
            at += 31.seconds
            failure = true
            val failed = requests.refresh(wallet, scope, RefreshCredentialStatusesRequest(
                setOf(WalletCredentialReference(0, "transient")), CredentialStatusRefreshMode.FORCE, context,
            )).single()
            assertTrue(failed.hasCheckErrors)
            assertEquals(CredentialStatusIssue.INVALID, failed.presentation.issue)
            assertTrue(failed.presentation.lastKnown)
            assertNull(store.readCredentialStatus("transient"))
            assertEquals(failed.presentation, requests.snapshot(wallet, scope, context).credentials.single { it.reference == failed.reference }.presentation)
            // Reload the active context at each boundary, including a change while signing is pending.
            val updatedReview = requests.snapshot(wallet, scope, context).credentials.associate { it.reference.credentialId to it.presentation.advisoryRevision }
            assertFailsWith<CredentialStatusReviewChangedException> {
                requests.withReviewedStatus(wallet, scope, updatedReview.keys, updatedReview, context) {
                    at += 31.seconds
                    failure = false
                    transientBody = transient.list(0u, ttl = 600)
                    requests.refresh(wallet, scope, RefreshCredentialStatusesRequest(mode = CredentialStatusRefreshMode.FORCE, statusContext = context))
                    currentCoroutineContext()[PresentationReviewGuard]!!.check()
                }
            }
            assertNull(store.readCredentialStatus("transient"))
            assertTrue(authorizedServices >= 9, "No authorized service may survive a request")
        } finally { client.close() }
    }

    @Test
    fun aCacheableConflictCannotPersistARestrictedObservationFromAnEarlierRequest() = runTest {
        val issuer = StatusTestIssuer.create()
        val store = InMemoryCredentialStore().apply { addCredential(StoredCredential("revocable", issuer.w3cCredential())) }
        val wallet = Wallet("wallet", credentialStores = listOf(store))
        var at = Instant.fromEpochSeconds(1_800_000_000)
        var body = issuer.w3cList(1u)
        var cacheControl = "no-store"
        val client = HttpClient(MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { respond(body, headers = Headers.build {
                append(HttpHeaders.ContentType, "application/vc+jwt"); append(HttpHeaders.CacheControl, cacheControl)
            }) }
        }))
        val requests = WalletCredentialStatusRequests(now = { at }) {
            WalletCredentialStatusService(it, client, now = { at }, trustPolicyId = "test-v1", parentScope = backgroundScope)
        }
        val scope = WalletCredentialStatusRequests.Scope(Any(), wallet.id)
        try {
            val context = assertNotNull(requests.createContext(wallet, scope).statusContext).id
            val initial = requests.refresh(wallet, scope, RefreshCredentialStatusesRequest(mode = CredentialStatusRefreshMode.FORCE, statusContext = context)).single()
            assertEquals(CredentialStatusKind.REVOKED, initial.presentation.kind)
            at += 31.seconds; cacheControl = "max-age=600"; body = issuer.w3cList(0u)
            val conflicting = requests.refresh(wallet, scope, RefreshCredentialStatusesRequest(mode = CredentialStatusRefreshMode.FORCE, statusContext = context)).single()
            assertTrue(conflicting.hasCheckErrors)
            assertTrue(conflicting.presentation.references.single().conflict)
            assertEquals(initial.presentation.lastCheckedAt, conflicting.presentation.lastCheckedAt)
            assertEquals(CredentialStatusKind.REVOKED, conflicting.presentation.kind)
            assertNull(store.readCredentialStatus("revocable"))
            assertEquals(conflicting.presentation, requests.snapshot(wallet, scope, context).credentials.single().presentation)
            assertEquals(CredentialStatusKind.NOT_CHECKED, requests.snapshot(wallet, scope).credentials.single().presentation.kind)
        } finally { client.close() }
    }

    @Test
    fun recordReservationsBoundThePoolAndRejectGrowthBeforeIssuerWork() = runTest {
        val issuer = StatusTestIssuer.create()
        val signed = issuer.credential()
        val store = InMemoryCredentialStore().apply { repeat(1000) { addCredential(StoredCredential("credential-$it", signed)) } }
        val wallet = Wallet("wallet", credentialStores = listOf(store))
        var issuerRequests = 0
        val client = HttpClient(MockEngine { issuerRequests++; error("Capacity must be checked before issuer work") })
        val requests = WalletCredentialStatusRequests {
            WalletCredentialStatusService(it, client, trustPolicyId = "test-v1", parentScope = backgroundScope)
        }
        val scope = WalletCredentialStatusRequests.Scope(Any(), wallet.id)
        try {
            val active = List(4) { assertNotNull(requests.createContext(wallet, scope).statusContext).id }
            assertEquals(429, assertFailsWith<StatusException> { requests.createContext(wallet, scope) }.status)
            store.addCredential(StoredCredential("one-too-many", signed))
            assertEquals(429, assertFailsWith<StatusException> {
                requests.refresh(wallet, scope, RefreshCredentialStatusesRequest(
                    setOf(WalletCredentialReference(0, "credential-0")), CredentialStatusRefreshMode.FORCE, active.first(),
                ))
            }.status)
            assertEquals(0, issuerRequests)
            store.removeCredential("one-too-many")
            requests.closeContext(scope, active.first())
            requests.createContext(wallet, scope)
        } finally { client.close() }
    }

    @Test
    fun contextsAreBoundedExpiringScopedAndExplicitlyClosed() = runTest {
        var at = Instant.fromEpochSeconds(1_800_000_000)
        val wallet = Wallet("wallet", credentialStores = listOf(InMemoryCredentialStore()))
        val requests = WalletCredentialStatusRequests(now = { at }) {
            WalletCredentialStatusService(it, now = { at }, trustPolicyId = "test-v1", parentScope = backgroundScope)
        }
        val backend = Any()
        val scope = WalletCredentialStatusRequests.Scope(backend, wallet.id, "generation", listOf("store-a"))
        val id = assertNotNull(requests.createContext(wallet, scope).statusContext).id
        for (foreign in listOf(
            WalletCredentialStatusRequests.Scope(Any(), wallet.id, "generation", listOf("store-a")),
            WalletCredentialStatusRequests.Scope(backend, "other-wallet", "generation", listOf("store-a")),
            WalletCredentialStatusRequests.Scope(backend, wallet.id, "new-generation", listOf("store-a")),
            WalletCredentialStatusRequests.Scope(backend, wallet.id, "generation", listOf("store-b")),
        )) {
            assertFailsWith<CredentialStatusReviewChangedException> { requests.snapshot(wallet, foreign, id) }
            requests.closeContext(foreign, id)
        }
        assertNotNull(requests.snapshot(wallet, scope, id).statusContext)
        at += 5.minutes
        assertFailsWith<CredentialStatusReviewChangedException> { requests.snapshot(wallet, scope, id) }
        val active = List(128) { assertNotNull(requests.createContext(wallet, scope).statusContext).id }
        assertEquals(429, assertFailsWith<StatusException> { requests.createContext(wallet, scope) }.status)
        requests.closeContext(scope, active.first())
        assertFailsWith<CredentialStatusReviewChangedException> { requests.snapshot(wallet, scope, active.first()) }
        requests.createContext(wallet, scope)
        requests.forget(WalletCredentialStatusRequests.Scope(backend, wallet.id))
        assertFailsWith<CredentialStatusReviewChangedException> { requests.snapshot(wallet, scope, active.last()) }
        requests.createContext(wallet, scope)
    }
}
