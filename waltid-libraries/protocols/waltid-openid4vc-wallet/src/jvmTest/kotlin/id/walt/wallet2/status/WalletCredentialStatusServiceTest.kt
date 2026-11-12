@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package id.walt.wallet2.status

import id.walt.credentials.status.*
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.data.WalletCredentialStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class WalletCredentialStatusServiceTest {
    private class Store : WalletCredentialStore, CredentialStatusSnapshotStore {
        val credentials = linkedMapOf<String, StoredCredential>()
        val snapshots = mutableMapOf<String, PersistedCredentialStatus>()
        override suspend fun getCredential(id: String) = credentials[id]
        override suspend fun listCredentials() = credentials.values.toList().asFlow()
        override suspend fun addCredential(entry: StoredCredential) { credentials[entry.id] = entry }
        override suspend fun removeCredential(id: String): Boolean { snapshots.remove(id); return credentials.remove(id) != null }
        override suspend fun readCredentialStatus(credentialId: String) = snapshots[credentialId]
        override suspend fun removeCredentialStatusIfCurrent(credential: StoredCredential): Boolean {
            if (credentials[credential.id]?.statusFingerprint() != credential.statusFingerprint()) return false
            snapshots.remove(credential.id)
            return true
        }
        override suspend fun saveCredentialStatusIfCurrent(credential: StoredCredential, status: PersistedCredentialStatus): Boolean {
            if (credentials[credential.id]?.statusFingerprint() != status.credentialFingerprint) return false
            snapshots[credential.id] = status
            return true
        }
    }

    @Test
    fun retryingABoundedBatchReachesDeferredCredentialsEvenWhenEarlierListsExpireImmediately() = runTest {
        val issuer = StatusTestIssuer.create()
        val store = Store()
        for (id in listOf("first", "second", "third")) store.addCredential(StoredCredential(id, issuer.credential()))
        val body = issuer.list(ttl = 0)
        val client = HttpClient(MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { respond(body, headers = Headers.build {
                append(HttpHeaders.ContentType, "application/statuslist+jwt"); append(HttpHeaders.CacheControl, "no-store")
            }) }
        }))
        val service = WalletCredentialStatusService(Wallet("wallet", credentialStores = listOf(store)), client,
            now = { Instant.fromEpochMilliseconds(1_800_000_000_000 + testScheduler.currentTime) }, parentScope = backgroundScope)
        try {
            val completed = mutableSetOf<String>()
            repeat(3) {
                val result = service.refresh(maxItems = 1)
                completed += result.single { it.outcome == CredentialStatusRefreshOutcome.COMPLETED }.reference.credentialId
                assertEquals(2, result.count { it.outcome == CredentialStatusRefreshOutcome.DEFERRED })
                advanceTimeBy(1000)
            }
            assertEquals(setOf("first", "second", "third"), completed)
        } finally { service.close(); client.close() }
    }

    @Test
    fun irreversibleW3cResultsAreRetainedWhileSuspensionAndTokenInvalidCanClear() = runTest {
        val issuer = StatusTestIssuer.create()
        for (purpose in listOf("revocation", "refresh", "suspension", "token")) {
            val token = purpose == "token"
            val store = Store().apply { addCredential(StoredCredential("resident",
                if (token) issuer.credential() else issuer.w3cCredential(purpose = purpose))) }
            var body = if (token) issuer.list(value = 1u) else issuer.w3cList(value = 1u, purpose = purpose)
            var at = Instant.fromEpochSeconds(1_800_000_000)
            val client = HttpClient(MockEngine(MockEngineConfig().apply {
                dispatcher = StandardTestDispatcher(testScheduler)
                addHandler { respond(body, headers = Headers.build {
                    append(HttpHeaders.ContentType, if (token) "application/statuslist+jwt" else "application/vc+jwt")
                }) }
            }))
            val service = WalletCredentialStatusService(Wallet("wallet", credentialStores = listOf(store)), client,
                now = { at }, trustPolicyId = "test-v1", parentScope = backgroundScope)
            try {
                service.refresh(mode = CredentialStatusRefreshMode.FORCE)
                val original = service.snapshot().credentials.single().status.entries.single().lastObservation()!!
                body = if (token) issuer.list() else issuer.w3cList(purpose = purpose)
                at += 1.seconds
                service.refresh(mode = CredentialStatusRefreshMode.FORCE)
                val updated = service.snapshot().credentials.single().status.entries.single()
                if (purpose in listOf("revocation", "refresh")) {
                    val conflict = assertIs<CredentialStatusEntry.Unavailable>(updated, purpose)
                    assertEquals(StatusFailure.CONFLICTING_IRREVERSIBLE_STATUS, conflict.reason, purpose)
                    assertEquals(original, conflict.previous, purpose)
                    assertEquals(original, store.snapshots.values.single().metadata.entries.single().lastObservation(), purpose)
                } else {
                    assertEquals(StatusMeaning.CLEAR, assertIs<CredentialStatusEntry.Observed>(updated, purpose).observation.meaning, purpose)
                }
            } finally { service.close(); client.close() }
        }
    }

    @Test
    fun localReadsPreserveCallerMetadataAndFailedRefreshKeepsThePriorObservation() = runTest {
        val issuer = StatusTestIssuer.create()
        val entry = StoredCredential("resident", issuer.credential(), metadata = buildJsonObject { put("custom", "retained") })
        val store = Store().apply { addCredential(entry) }
        var body = issuer.list(value = 1u)
        var status = HttpStatusCode.OK
        var requests = 0
        val client = HttpClient(MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { requests++; respond(body, status, Headers.build {
                append(HttpHeaders.ContentType, "application/statuslist+jwt"); append(HttpHeaders.CacheControl, "max-age=600")
            }) }
        }))
        val service = WalletCredentialStatusService(Wallet("wallet", credentialStores = listOf(store)), client,
            now = { Instant.fromEpochMilliseconds(1_800_000_000_000 + testScheduler.currentTime) },
            trustPolicyId = "test-direct-trust-v1", parentScope = backgroundScope)
        try {
            val read = service.readCredentials().single()
            assertEquals(entry.metadata, read.credential.metadata)
            assertEquals(CredentialStatusKind.NOT_CHECKED, read.presentation.kind)
            assertEquals(0, requests)
            assertEquals(Instant.fromEpochMilliseconds(1_800_000_000_000), service.snapshot().nextRefreshAt)
            service.refresh(mode = CredentialStatusRefreshMode.FORCE)
            val first = service.snapshot().credentials.single().status.entries.single().lastObservation()!!
            assertEquals(first.freshUntil, service.snapshot().nextRefreshAt)
            status = HttpStatusCode.ServiceUnavailable
            advanceTimeBy(1000)
            service.refresh(mode = CredentialStatusRefreshMode.FORCE)
            val failed = assertIs<CredentialStatusEntry.Unavailable>(service.snapshot().credentials.single().status.entries.single())
            assertEquals(StatusFailure.TRANSPORT, failed.reason)
            assertEquals(first, failed.previous)
            assertEquals(failed.attemptedAt + 30.seconds, service.snapshot().nextRefreshAt)
            assertEquals(entry.metadata, service.readCredentials().single().credential.metadata)
            assertEquals(StatusMeaning.INVALID, failed.previous?.meaning)
            assertEquals(2, requests)
        } finally { service.close(); client.close() }
    }

    @Test
    fun freshnessExpiryEmitsWithoutNetworkAndRestartRequiresTheSameTrustPolicy() = runTest {
        val issuer = StatusTestIssuer.create()
        val store = Store().apply { addCredential(StoredCredential("resident", issuer.credential())) }
        val body = issuer.list(ttl = 30)
        var requests = 0
        val client = HttpClient(MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { requests++; respond(body, headers = Headers.build {
                append(HttpHeaders.ContentType, "application/statuslist+jwt"); append(HttpHeaders.CacheControl, "max-age=600")
            }) }
        }))
        fun service(policy: String?) = WalletCredentialStatusService(Wallet("wallet", credentialStores = listOf(store)), client,
            now = { Instant.fromEpochMilliseconds(1_800_000_000_000 + testScheduler.currentTime) },
            trustPolicyId = policy, parentScope = backgroundScope)
        val initial = service("policy-v1")
        try {
            initial.refresh()
            val same = service("policy-v1")
            assertEquals(CredentialStatusFreshness.FRESH, same.snapshot().credentials.single().presentation.freshness)
            same.close()
            val changed = service("policy-v2")
            assertEquals(CredentialStatusFreshness.STALE, changed.snapshot().credentials.single().presentation.freshness)
            changed.close()
            val unknown = service(null)
            assertEquals(CredentialStatusFreshness.STALE, unknown.snapshot().credentials.single().presentation.freshness)
            unknown.close()
            val values = async { initial.observe().take(2).toList() }
            runCurrent()
            advanceTimeBy(30_001); runCurrent()
            val snapshots = values.await()
            assertEquals(CredentialStatusFreshness.FRESH, snapshots.first().credentials.single().presentation.freshness)
            assertEquals(CredentialStatusKind.OUT_OF_DATE, snapshots.last().credentials.single().presentation.kind)
            assertEquals(1, requests)
        } finally { initial.close(); client.close() }
    }

    @Test
    fun deletingCredentialOrDetachingStoreDuringRefreshCannotPersistAResult() = runTest {
        val issuer = StatusTestIssuer.create()
        val body = issuer.list()
        for (detachStore in listOf(false, true)) {
            val store = Store().apply { addCredential(StoredCredential("resident", issuer.credential())) }
            val stores = mutableListOf<WalletCredentialStore>(store)
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val client = HttpClient(MockEngine(MockEngineConfig().apply {
                dispatcher = StandardTestDispatcher(testScheduler)
                addHandler { entered.complete(Unit); release.await(); respond(body,
                    headers = Headers.build { append(HttpHeaders.ContentType, "application/statuslist+jwt") }) }
            }))
            val service = WalletCredentialStatusService(Wallet("wallet", credentialStores = stores), client, parentScope = backgroundScope)
            try {
                val work = async { service.refresh(mode = CredentialStatusRefreshMode.FORCE) }
                entered.await()
                if (detachStore) stores.clear() else store.removeCredential("resident")
                release.complete(Unit)
                assertEquals(CredentialStatusRefreshOutcome.REMOVED, work.await().single().outcome)
                assertTrue(store.snapshots.isEmpty())
                assertTrue(service.snapshot().credentials.isEmpty())
            } finally { service.close(); client.close() }
        }
    }

    @Test
    fun cancellingOneOwnerDoesNotCancelACoalescedRequestForAnother() = runTest {
        val issuer = StatusTestIssuer.create()
        val store = Store().apply { addCredential(StoredCredential("resident", issuer.credential())) }
        val body = issuer.list()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var requests = 0
        val client = HttpClient(MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { requests++; entered.complete(Unit); release.await(); respond(body,
                headers = Headers.build { append(HttpHeaders.ContentType, "application/statuslist+jwt") }) }
        }))
        val service = WalletCredentialStatusService(Wallet("wallet", credentialStores = listOf(store)), client, parentScope = backgroundScope)
        try {
            val first = async { service.refresh(mode = CredentialStatusRefreshMode.FORCE) }
            entered.await()
            val second = async { service.refresh(mode = CredentialStatusRefreshMode.FORCE) }
            runCurrent()
            first.cancelAndJoin()
            release.complete(Unit)
            assertEquals(CredentialStatusRefreshOutcome.COMPLETED, second.await().single().outcome)
            assertEquals(1, requests)
        } finally { service.close(); client.close() }
    }
    @Test
    fun noStorePurgesTheManagedSnapshotAndClosingFinishesObservers() = runTest {
        val issuer = StatusTestIssuer.create()
        val store = Store().apply { addCredential(StoredCredential("resident", issuer.credential())) }
        val body = issuer.list()
        var cacheControl = "max-age=600"
        val client = HttpClient(MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { respond(body, headers = Headers.build {
                append(HttpHeaders.ContentType, "application/statuslist+jwt"); append(HttpHeaders.CacheControl, cacheControl)
            }) }
        }))
        val wallet = Wallet("wallet", credentialStores = listOf(store))
        val service = WalletCredentialStatusService(wallet, client, now = { Instant.fromEpochSeconds(1_800_000_000) }, trustPolicyId = "test-v1", parentScope = backgroundScope)
        try {
            service.refresh(mode = CredentialStatusRefreshMode.FORCE)
            assertTrue(store.snapshots.isNotEmpty())
            cacheControl = "no-store"
            service.refresh(mode = CredentialStatusRefreshMode.FORCE)
            assertTrue(store.snapshots.isEmpty())
            val reopened = WalletCredentialStatusService(wallet, client, trustPolicyId = "test-v1", parentScope = backgroundScope)
            assertEquals(CredentialStatusKind.NOT_CHECKED, reopened.snapshot().credentials.single().presentation.kind)
            reopened.close()
            val observer = async { service.observe().toList() }
            runCurrent()
            service.close(); runCurrent()
            assertTrue(observer.isCompleted)
            assertTrue(observer.await().isNotEmpty())
        } finally { service.close(); client.close() }
    }

    @Test
    fun reviewedWarningsIgnoreNewCheckTimesButDetectASelectedFreshnessChange() = runTest {
        val issuer = StatusTestIssuer.create()
        val store = Store().apply { addCredential(StoredCredential("resident", issuer.credential())) }
        val body = issuer.list(ttl = 30)
        val client = HttpClient(MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { respond(body, headers = Headers.build { append(HttpHeaders.ContentType, "application/statuslist+jwt") }) }
        }))
        var at = Instant.fromEpochSeconds(1_800_000_000)
        val service = WalletCredentialStatusService(Wallet("wallet", credentialStores = listOf(store)), client, now = { at }, parentScope = backgroundScope)
        try {
            service.refresh(mode = CredentialStatusRefreshMode.FORCE)
            val rendered = service.snapshot().credentials.single().presentation.advisoryRevision
            val guard = service.reviewGuard(setOf("resident"), mapOf("resident" to rendered, "unselected" to "ignored"))
            at += kotlin.time.Duration.parse("1s")
            service.refresh(mode = CredentialStatusRefreshMode.FORCE)
            guard.check()
            at += kotlin.time.Duration.parse("31s")
            assertFailsWith<CredentialStatusReviewChangedException> { guard.check() }
            service.reviewGuard(setOf("resident"), mapOf("resident" to service.snapshot().credentials.single().presentation.advisoryRevision)).check()
        } finally { service.close(); client.close() }
    }

}
