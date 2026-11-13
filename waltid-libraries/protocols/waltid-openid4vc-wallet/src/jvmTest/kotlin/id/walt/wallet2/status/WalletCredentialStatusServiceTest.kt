@file:OptIn(ExperimentalCoroutinesApi::class)

package id.walt.wallet2.status

import id.walt.credentials.status.Check
import id.walt.credentials.status.CheckFailure
import id.walt.credentials.status.CredentialStatus
import id.walt.credentials.status.CredentialStatusEntry
import id.walt.credentials.status.CredentialStatusFreshness
import id.walt.credentials.status.CredentialStatusKind
import id.walt.credentials.status.CredentialStatusRefreshAction
import id.walt.credentials.status.Observation
import id.walt.credentials.status.StatusMeaning
import id.walt.credentials.status.StatusTestIssuer
import id.walt.credentials.status.TokenStatus
import id.walt.credentials.status.entries
import id.walt.credentials.status.failure
import id.walt.credentials.status.hasConflict
import id.walt.credentials.status.lastObservation
import id.walt.credentials.status.meaning
import id.walt.credentials.status.presentation
import id.walt.credentials.formats.W3C11
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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.Duration
import kotlinx.coroutines.ExperimentalCoroutinesApi

class WalletCredentialStatusServiceTest {
    @Test
    fun snapshotsDetachCollectionsValidateTheirTimeAndKeepStoreScopedIdentities() {
        val at = Instant.fromEpochSeconds(1_800_000_000)
        val first = WalletCredentialStatus(WalletCredentialReference(0, "same-id"), CredentialStatus.references(), at)
        val second = WalletCredentialStatus(WalletCredentialReference(1, "same-id"), CredentialStatus.references(), at)
        val records = mutableListOf(first, second)
        val active = mutableSetOf(first.reference, second.reference)
        val snapshot = WalletCredentialStatusSnapshot(at, records, active)
        records.clear(); active.clear()
        assertEquals(listOf(first, second), snapshot.credentials)
        assertEquals(setOf(first.reference, second.reference), snapshot.refreshing)
        runCatching { (snapshot.credentials as MutableList<WalletCredentialStatus>).clear() }
        runCatching { (snapshot.refreshing as MutableSet<WalletCredentialReference>).clear() }
        assertEquals(listOf(first, second), snapshot.credentials)
        assertEquals(setOf(first.reference, second.reference), snapshot.refreshing)
        assertFailsWith<IllegalArgumentException> { WalletCredentialStatusSnapshot(at, listOf(first, first)) }
        assertFailsWith<IllegalArgumentException> { WalletCredentialStatusSnapshot(at + 1.seconds, listOf(first)) }
    }

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
    fun detachedReviewBindsPolicySignedBytesAndCurrentManagedEvidenceWithoutNetworking() = runTest {
        val issuer = StatusTestIssuer.create()
        val credential = StoredCredential("resident", issuer.credential())
        val store = Store().apply { addCredential(credential) }
        var at = Instant.fromEpochSeconds(1_800_000_000)
        var cacheControl = "no-store"
        var value = 0u
        var requests = 0
        val client = HttpClient(MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler {
                requests++
                respond(issuer.list(value, ttl = 600), headers = Headers.build {
                    append(HttpHeaders.ContentType, "application/statuslist+jwt")
                    append(HttpHeaders.CacheControl, cacheControl)
                })
            }
        }))
        fun service(walletId: String = "wallet", policy: String? = "test-v1", managed: Boolean = true) = WalletCredentialStatusService(
            Wallet(walletId, credentialStores = listOf(if (managed) store else object : WalletCredentialStore by store {})),
            client, now = { at }, trustPolicyId = policy, parentScope = backgroundScope,
        )
        try {
            val detached = service().use { initial ->
                initial.refresh(mode = CredentialStatusRefreshMode.FORCE)
                initial.captureReviewState()
            }
            assertEquals(1, detached.recordCount)
            for (mismatch in listOf(service(walletId = "other"), service(policy = "other-policy"), service(policy = null))) {
                mismatch.use { assertFailsWith<CredentialStatusReviewChangedException> { it.restoreReviewState(detached) } }
            }
            // A later ordinary request's managed write must take precedence over the old clear review.
            at += 31.seconds; cacheControl = "max-age=600"; value = 1u
            service().use { it.refresh(mode = CredentialStatusRefreshMode.FORCE) }
            service().use { restored ->
                restored.restoreReviewState(detached)
                assertEquals(CredentialStatusKind.INVALID, restored.snapshot().credentials.single().presentation.kind)
            }
            assertEquals(2, requests)
            store.addCredential(credential.copy(credential = issuer.credential(index = 1)))
            service().use { replaced ->
                replaced.restoreReviewState(detached)
                assertEquals(CredentialStatusKind.NOT_CHECKED, replaced.snapshot().credentials.single().presentation.kind)
            }
            assertEquals(2, requests, "Restoring a review must remain local")
            // Custom adapters without snapshot storage also retain evidence only for this active review.
            val custom = service(managed = false).use { initial ->
                initial.refresh(mode = CredentialStatusRefreshMode.FORCE)
                initial.captureReviewState()
            }
            assertEquals(1, custom.recordCount)
            service(managed = false).use { reopened ->
                reopened.restoreReviewState(custom)
                assertEquals(CredentialStatusKind.INVALID, reopened.snapshot().credentials.single().presentation.kind)
            }
            assertEquals(3, requests)
        } finally { client.close() }
    }

    @Test
    fun reviewGuardReconcilesStatusCommittedByAnotherServiceWithoutIssuerNetworking() = runTest {
        val issuer = StatusTestIssuer.create()
        val store = Store().apply { addCredential(StoredCredential("resident", issuer.credential())) }
        var at = Instant.fromEpochSeconds(1_800_000_000)
        var body = issuer.list()
        var requests = 0
        val client = HttpClient(MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler {
                requests++
                respond(body, headers = Headers.build {
                    append(HttpHeaders.ContentType, "application/statuslist+jwt")
                    append(HttpHeaders.CacheControl, "max-age=600")
                })
            }
        }))
        fun service() = WalletCredentialStatusService(Wallet("wallet", credentialStores = listOf(store)), client,
            now = { at }, trustPolicyId = "test-v1", parentScope = backgroundScope)
        val presenting = service()
        val refreshing = service()
        try {
            presenting.refresh(mode = CredentialStatusRefreshMode.FORCE)
            val reviewed = presenting.snapshot().credentials.single().presentation.advisoryRevision
            val guard = presenting.reviewGuard(setOf("resident"), mapOf("resident" to reviewed))
            guard.check()

            at += 1.seconds
            body = issuer.list(value = 1u)
            refreshing.refresh(mode = CredentialStatusRefreshMode.FORCE)

            assertFailsWith<CredentialStatusReviewChangedException> { guard.check() }
            assertEquals(CredentialStatusKind.INVALID, presenting.readCredential("resident")!!.presentation.kind)
            assertEquals(2, requests, "Guard and credential reads only reconcile locally stored evidence")
        } finally { presenting.close(); refreshing.close(); client.close() }
    }

    @Test
    fun reconcilingAnOlderStoreWritePreservesNewerInstanceOwnedNoStoreEvidence() = runTest {
        val issuer = StatusTestIssuer.create()
        val store = Store().apply { addCredential(StoredCredential("resident", issuer.credential())) }
        var at = Instant.fromEpochSeconds(1_800_000_000)
        var body = issuer.list()
        var cacheControl = "max-age=600"
        var requests = 0
        val client = HttpClient(MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler {
                requests++
                respond(body, headers = Headers.build {
                    append(HttpHeaders.ContentType, "application/statuslist+jwt")
                    append(HttpHeaders.CacheControl, cacheControl)
                })
            }
        }))
        val service = WalletCredentialStatusService(Wallet("wallet", credentialStores = listOf(store)), client,
            now = { at }, trustPolicyId = "test-v1", parentScope = backgroundScope)
        try {
            service.refresh(mode = CredentialStatusRefreshMode.FORCE)
            val older = store.snapshots.getValue("resident")
            at += 1.seconds
            body = issuer.list(value = 1u)
            cacheControl = "no-store"
            service.refresh(mode = CredentialStatusRefreshMode.FORCE)
            val latest = service.snapshot().credentials.single()
            assertEquals(CredentialStatusKind.INVALID, latest.presentation.kind)
            assertTrue(store.snapshots.isEmpty())

            // A slower independent writer can finish an older check after this instance's refresh.
            store.snapshots["resident"] = older
            val reconciled = service.snapshot().credentials.single()
            assertEquals(latest.status, reconciled.status)
            service.reviewGuard(setOf("resident"), mapOf("resident" to latest.presentation.advisoryRevision)).check()
            assertEquals(2, requests)
            assertEquals(older, store.snapshots.getValue("resident"), "A read must not persist instance-only evidence")
        } finally { service.close(); client.close() }
    }

    @Test
    fun failedRefreshCannotPersistRetainedNoStoreEvidenceButANewCacheableObservationCan() = runTest {
        val issuer = StatusTestIssuer.create()
        val store = Store().apply { addCredential(StoredCredential("resident", issuer.credential())) }
        var at = Instant.fromEpochSeconds(1_800_000_000)
        var body = issuer.list(value = 1u)
        var response = HttpStatusCode.OK
        var cacheControl = "no-store"
        val client = HttpClient(MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { respond(body, response, Headers.build {
                append(HttpHeaders.ContentType, "application/statuslist+jwt")
                append(HttpHeaders.CacheControl, cacheControl)
            }) }
        }))
        val service = WalletCredentialStatusService(Wallet("wallet", credentialStores = listOf(store)), client,
            now = { at }, trustPolicyId = "test-v1", parentScope = backgroundScope)
        try {
            service.refresh(mode = CredentialStatusRefreshMode.FORCE)
            val observed = service.snapshot().credentials.single().status.entries.single().lastObservation()
            assertTrue(store.snapshots.isEmpty())

            at += 1.seconds
            response = HttpStatusCode.ServiceUnavailable
            service.refresh(mode = CredentialStatusRefreshMode.FORCE)
            val retained = service.snapshot().credentials.single().status.entries.single()
            assertEquals(CheckFailure.Reason.TRANSPORT, retained.failure)
            assertEquals(observed, retained.lastObservation())
            assertTrue(store.snapshots.isEmpty(), "Failure cannot make the retained no-store observation persistable")

            at += 1.seconds
            response = HttpStatusCode.OK
            cacheControl = "max-age=600"
            body = issuer.list()
            service.refresh(mode = CredentialStatusRefreshMode.FORCE)
            assertEquals(StatusMeaning.CLEAR, store.snapshots.values.single().metadata.entries.single().meaning)
        } finally { service.close(); client.close() }
    }

    @Test
    fun cacheableConflictingResponseCannotPersistAnEarlierNoStoreObservation() = runTest {
        val issuer = StatusTestIssuer.create()
        val store = Store().apply { addCredential(StoredCredential("resident", issuer.w3cCredential())) }
        var at = Instant.fromEpochSeconds(1_800_000_000)
        var body = issuer.w3cList(value = 1u)
        var cacheControl = "no-store"
        val client = HttpClient(MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { respond(body, headers = Headers.build {
                append(HttpHeaders.ContentType, "application/vc+jwt")
                append(HttpHeaders.CacheControl, cacheControl)
            }) }
        }))
        val service = WalletCredentialStatusService(Wallet("wallet", credentialStores = listOf(store)), client,
            now = { at }, trustPolicyId = "test-v1", parentScope = backgroundScope)
        try {
            service.refresh(mode = CredentialStatusRefreshMode.FORCE)
            val observed = service.snapshot().credentials.single().status.entries.single().lastObservation()
            at += 1.seconds
            cacheControl = "max-age=600"
            body = issuer.w3cList()
            service.refresh(mode = CredentialStatusRefreshMode.FORCE)
            val conflict = service.snapshot().credentials.single().status.entries.single()
            assertTrue(conflict.hasConflict)
            assertEquals(observed, conflict.lastObservation())
            assertTrue(store.snapshots.isEmpty(), "A new cacheable response cannot change the retained observation's storage policy")
        } finally { service.close(); client.close() }
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
                    val conflict = assertIs<Check.Conflict<*>>(assertIs<CredentialStatusEntry.Bitstring>(updated).check, purpose)
                    assertEquals(original, conflict.retained, purpose)
                    assertEquals(original, store.snapshots.values.single().metadata.entries.single().lastObservation(), purpose)
                } else {
                    assertEquals(StatusMeaning.CLEAR, updated.meaning, purpose)
                }
            } finally { service.close(); client.close() }
        }
    }

    @Test
    fun localReadsPreserveCallerMetadataAndFailedRefreshKeepsThePriorObservation() = runTest {
        val issuer = StatusTestIssuer.create()
        val signed = assertIs<W3C11>(issuer.credential())
        val entry = StoredCredential("resident", signed.copy(credentialData = JsonObject(signed.credentialData - "status")),
            metadata = buildJsonObject { put("custom", "retained") })
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
            val failedEntry = assertIs<CredentialStatusEntry.Token>(service.snapshot().credentials.single().status.entries.single())
            val failed = assertIs<Check.Failed<TokenStatus>>(failedEntry.check)
            assertEquals(CheckFailure.Reason.TRANSPORT, failed.cause)
            assertEquals<Observation<*>?>(first, failed.previous)
            assertEquals(failed.attemptedAt + 30.seconds, service.snapshot().nextRefreshAt)
            assertEquals(entry.metadata, service.readCredentials().single().credential.metadata)
            assertEquals(StatusMeaning.INVALID, failedEntry.meaning)
            assertEquals(2, requests)
            val malformedProjection = assertIs<W3C11>(entry.credential).copy(
                credentialData = JsonObject(entry.credential.credentialData + ("status" to JsonPrimitive(true))))
            store.credentials[entry.id] = entry.copy(credential = malformedProjection)
            advanceTimeBy(1000)
            service.refresh(mode = CredentialStatusRefreshMode.FORCE)
            val retained = assertIs<CredentialStatusEntry.Token>(service.snapshot().credentials.single().status.entries.single())
            val latestFailure = assertIs<Check.Failed<TokenStatus>>(retained.check)
            assertEquals(CheckFailure.Reason.TRANSPORT, latestFailure.cause)
            assertEquals<Observation<*>?>(first, latestFailure.previous,
                "Projection drift must not erase the original canonical evidence after a failed refresh")
            assertEquals(StatusMeaning.INVALID, retained.meaning)
            assertEquals(3, requests, "A malformed projection must not suppress the canonical signed reference")
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
            at += Duration.parse("1s")
            service.refresh(mode = CredentialStatusRefreshMode.FORCE)
            guard.check()
            at += Duration.parse("31s")
            assertFailsWith<CredentialStatusReviewChangedException> { guard.check() }
            service.reviewGuard(setOf("resident"), mapOf("resident" to service.snapshot().credentials.single().presentation.advisoryRevision)).check()
        } finally { service.close(); client.close() }
    }

    @Test
    fun notYetValidBecomesDueAtItsStartWithoutTreatingItAsExpired() = runTest {
        val issuer = StatusTestIssuer.create()
        val start = Instant.fromEpochSeconds(1_800_000_060)
        val store = Store().apply { addCredential(StoredCredential("resident", issuer.credential(validFrom = start.epochSeconds))) }
        var requests = 0
        val client = HttpClient(MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { requests++; respond(issuer.list(), headers = Headers.build { append(HttpHeaders.ContentType, "application/statuslist+jwt") }) }
        }))
        val service = WalletCredentialStatusService(Wallet("wallet", credentialStores = listOf(store)), client,
            now = { Instant.fromEpochMilliseconds(1_800_000_000_000 + testScheduler.currentTime) }, parentScope = backgroundScope)
        try {
            service.refresh(mode = CredentialStatusRefreshMode.FORCE)
            val waiting = service.snapshot()
            assertEquals(CheckFailure.NotYetValid(start), waiting.credentials.single().presentation.failure)
            assertEquals(CredentialStatusRefreshAction.NONE, waiting.credentials.single().presentation.action)
            assertEquals(start, waiting.nextRefreshAt)
            assertEquals(0, requests)
            assertEquals(CredentialStatusRefreshOutcome.UNCHANGED, service.refresh(mode = CredentialStatusRefreshMode.FORCE).single().outcome)
            val snapshots = async { service.observe().take(2).toList() }
            runCurrent(); advanceTimeBy(60_001); runCurrent()
            assertEquals(CredentialStatusRefreshAction.RETRY, snapshots.await().last().credentials.single().presentation.action)
            service.refresh()
            assertEquals(1, requests)
            assertEquals(CredentialStatusKind.CHECKED, service.snapshot().credentials.single().presentation.kind)
        } finally { service.close(); client.close() }
    }

    @Test
    fun restoreRejectsAnOldSchemaAndSameIdEvidenceWithADifferentProtocolOrPurpose() = runTest {
        val issuer = StatusTestIssuer.create()
        val at = Instant.fromEpochSeconds(1_800_000_000)
        val store = Store().apply { addCredential(StoredCredential("resident", issuer.w3cCredential())) }
        var requests = 0
        val client = HttpClient(MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { requests++; respond(issuer.w3cList(), headers = Headers.build { append(HttpHeaders.ContentType, "application/vc+jwt") }) }
        }))
        fun service() = WalletCredentialStatusService(Wallet("wallet", credentialStores = listOf(store)), client,
            now = { at }, trustPolicyId = "stable-policy", parentScope = backgroundScope)
        val first = service()
        try { first.refresh(mode = CredentialStatusRefreshMode.FORCE) } finally { first.close() }
        val valid = assertNotNull(store.snapshots["resident"])
        val id = valid.metadata.entries.single().referenceId
        val wrongProtocol = Json.decodeFromString(CredentialStatus.serializer(), """{"type":"references","entries":[{"type":"mso","referenceId":"$id","check":{"type":"observed","observation":{"value":"REVOKED","checkedAt":"$at","freshUntil":"$at"}}}]}""")
        val wrongPurpose = Json.decodeFromString(CredentialStatus.serializer(), Json.encodeToString(CredentialStatus.serializer(), valid.metadata)
            .replace("\"purpose\":\"revocation\"", "\"purpose\":\"suspension\""))
        assertNotEquals(valid.metadata, wrongPurpose)
        try {
            for (saved in listOf(valid.copy(schemaVersion = 1), valid.copy(metadata = wrongProtocol), valid.copy(metadata = wrongPurpose))) {
                store.snapshots["resident"] = saved
                val reopened = service()
                try { assertEquals(CredentialStatusKind.NOT_CHECKED, reopened.snapshot().credentials.single().presentation.kind) }
                finally { reopened.close() }
            }
            assertEquals(1, requests, "Restore and local reads must not contact the status endpoint")
        } finally { client.close() }
    }

}
