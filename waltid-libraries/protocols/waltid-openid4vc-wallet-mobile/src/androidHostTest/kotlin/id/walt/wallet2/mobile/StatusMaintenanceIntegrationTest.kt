package id.walt.wallet2.mobile

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import id.walt.credentials.status.CredentialStatusFreshness
import id.walt.credentials.status.StatusMeaning
import id.walt.credentials.status.StatusTestIssuer
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.data.WalletCredentialStore
import id.walt.wallet2.persistence.db.WalletPersistenceDatabase
import id.walt.wallet2.persistence.stores.SqlDelightCredentialStore
import id.walt.wallet2.persistence.stores.SqlDelightStatusMaintenanceStore
import id.walt.wallet2.status.CredentialStatusCommit
import id.walt.wallet2.status.CredentialStatusInput
import id.walt.wallet2.status.CredentialStatusInventory
import id.walt.wallet2.status.CredentialStatusNoticeState
import id.walt.wallet2.status.CredentialStatusNoticeStore
import id.walt.wallet2.status.CredentialStatusRefreshMode
import id.walt.wallet2.status.CredentialStatusRefreshOutcome
import id.walt.wallet2.status.CredentialStatusSnapshotStore
import id.walt.wallet2.status.CredentialStatusStoredRecord
import id.walt.wallet2.status.WalletCredentialStatusService
import id.walt.wallet2.status.WalletCredentialReference
import id.walt.wallet2.status.reviewGuard
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

class StatusMaintenanceIntegrationTest {
    @Test
    fun coldWorkersMovePastNonRetainableEvidence() = withWallet {
        val first = add("a", cache = "no-store", value = 1u)
        val second = add("b")

        assertEquals(CredentialStatusMaintenanceResult.Finished(1, 0, 1), wake(1))
        assertEquals(CredentialStatusMaintenanceResult.Finished(1, 0, 0), wake(1))
        assertEquals(listOf(first.uri, second.uri), requests)
        assertNull(store.readCredentialStatus("a"))
        assertNotNull(store.readCredentialStatus("b"))
        assertNull(assertIs<CredentialStatusInput.Signed>(store.readStatusInput("a")).notice)
    }

    @Test
    fun multiplePendingNoticesKeepCheckOrderAndAcknowledgeIndependently() = withWallet {
        add("z", value = 2u)
        add("a", value = 1u)
        val first = WalletCredentialReference(0, "z")
        val second = WalletCredentialReference(0, "a")
        val policy = managedCredentialStatusTrustPolicy()
        var now = Clock.System.now()
        fun openService() = WalletCredentialStatusService(
            Wallet("review", credentialStores = listOf(store)), client, now = { now }, trustPolicyId = policy,
        )
        openService().use { wallet ->
            wallet.refresh(setOf(first))
            now += 1.seconds
            wallet.refresh(setOf(second))
            val pending = wallet.snapshot().pendingNotices
            assertEquals(listOf(first, second), pending.map { it.reference })
            wallet.acknowledgeNotice(first, pending.first().version)
            assertEquals(listOf(pending.last()), wallet.snapshot().pendingNotices)
        }
        openService().use { wallet ->
            assertEquals(second, wallet.snapshot().pendingNotices.single().reference)
        }
    }

    @Test
    fun statusWorkIgnoresDisplayDataAndIsolatesAnUnreadableSignedRecord() = withWallet {
        val selected = add("a")
        val unrelated = add("b")
        add("c")
        driver.execute(null, "UPDATE credentials SET metadata = '[]' WHERE id = 'b'", 0)
        driver.execute(null, "UPDATE credentials SET serialized_credential = 'broken' WHERE id = 'c'", 0)

        service().use { foreground ->
            val status = foreground.selectedStatuses(setOf("a")).single()
            foreground.reviewGuard(setOf("a"), mapOf("a" to status.presentation.advisoryRevision)).check()
            assertEquals(CredentialStatusMaintenanceResult.Finished(3, 0, 0), wake())
            assertEquals(listOf(selected.uri, unrelated.uri), requests)
            assertEquals(3, foreground.snapshot().credentials.size)
        }
        assertNotNull(store.readCredentialStatus("b"))
        assertNull(store.readCredentialStatus("c"))
    }

    @Test
    fun aMatchingForegroundPolicyReusesFreshWorkerEvidenceAndAnotherPolicyRechecks() = withWallet {
        val issuer = add("a")
        wake()
        val policy = assertNotNull(managedCredentialStatusTrustPolicy())
        var pointReads = 0
        val inventory = object : WalletCredentialStore by store, CredentialStatusInventory by store {
            override suspend fun readCredentialStatusRecord(credentialId: String): CredentialStatusStoredRecord? {
                pointReads++
                return store.readCredentialStatusRecord(credentialId)
            }
        }
        service(policy, inventory).use { foreground ->
            assertEquals(CredentialStatusFreshness.FRESH, foreground.snapshot().credentials.single().presentation.freshness)
            assertEquals(CredentialStatusRefreshOutcome.UNCHANGED, foreground.refresh().single().outcome)
            assertEquals(0, pointReads, "A fresh due check should reuse the evidence from the inventory page")
            assertEquals(listOf(issuer.uri), requests)
        }
        service("different-issuer-trust").use { changed ->
            assertEquals(CredentialStatusFreshness.STALE, changed.snapshot().credentials.single().presentation.freshness)
            assertEquals(CredentialStatusRefreshOutcome.COMPLETED, changed.refresh().single().outcome)
            assertEquals(listOf(issuer.uri, issuer.uri), requests)
        }
    }

    @Test
    fun committedAdverseChangesHaveOneSharedAcknowledgementAndRecover() = withWallet {
        val issuer = add("a", value = 2u)
        assertTrue(assertIs<CredentialStatusMaintenanceResult.Finished>(wake()).hasNewNotice)
        service(managedCredentialStatusTrustPolicy()).use { foreground ->
            val suspension = foreground.snapshot().pendingNotices.single()
            assertEquals(StatusMeaning.SUSPENDED, suspension.kind)
            foreground.refresh(mode = CredentialStatusRefreshMode.FORCE)
            assertEquals(suspension.version, foreground.snapshot().pendingNotices.single().version)

            setList(issuer, value = 1u)
            foreground.refresh(mode = CredentialStatusRefreshMode.FORCE)
            val invalid = foreground.snapshot().pendingNotices.single()
            assertEquals(StatusMeaning.INVALID, invalid.kind)
            assertNotEquals(suspension.version, invalid.version)
            foreground.acknowledgeNotice(suspension.reference, suspension.version)
            assertEquals(invalid, foreground.snapshot().pendingNotices.single())
            foreground.acknowledgeNotice(invalid.reference, invalid.version)
            assertTrue(foreground.snapshot().pendingNotices.isEmpty())
        }
        service(managedCredentialStatusTrustPolicy()).use { reopened ->
            assertTrue(reopened.snapshot().pendingNotices.isEmpty())
            reopened.refresh(mode = CredentialStatusRefreshMode.FORCE)
            assertTrue(reopened.snapshot().pendingNotices.isEmpty())
            setList(issuer, value = 0u)
            reopened.refresh(mode = CredentialStatusRefreshMode.FORCE)
            assertNull(assertIs<CredentialStatusInput.Signed>(store.readStatusInput("a")).notice)
        }
        assertFalse(assertIs<CredentialStatusMaintenanceResult.Finished>(wake()).hasNewNotice)
    }

    @Test
    fun restrictedEvidenceCanWarnInSessionAndDoesNotReachTheNextWalletInstance() = withWallet {
        add("a", cache = "no-store", value = 1u)
        service().use { foreground ->
            foreground.refresh()
            val change = foreground.snapshot().pendingNotices.single()
            assertEquals(StatusMeaning.INVALID, change.kind)
            foreground.acknowledgeNotice(change.reference, change.version)
            foreground.refresh(mode = CredentialStatusRefreshMode.FORCE)
            assertTrue(foreground.snapshot().pendingNotices.isEmpty())
        }
        service().use { assertTrue(it.snapshot().pendingNotices.isEmpty()) }
        assertNull(store.readCredentialStatus("a"))
        assertNull(assertIs<CredentialStatusInput.Signed>(store.readStatusInput("a")).notice)
    }

    @Test
    fun anAcknowledgedStatusKeepsItsVersionWhenEvidenceChangesRetention() {
        val cacheable = "max-age=600"
        val restricted = "no-store"
        for ((initial, next) in listOf(cacheable to restricted, restricted to cacheable)) {
            withWallet {
                val issuer = add("a", cache = initial, value = 2u)
                val policy = managedCredentialStatusTrustPolicy()
                service(policy).use { foreground ->
                    foreground.refresh()
                    val original = foreground.snapshot().pendingNotices.single()
                    foreground.acknowledgeNotice(original.reference, original.version)

                    setList(issuer, value = 2u, cache = next)
                    foreground.refresh(mode = CredentialStatusRefreshMode.FORCE)
                    assertTrue(foreground.snapshot().pendingNotices.isEmpty(), "Duplicate warning for $initial to $next")
                    val input = assertIs<CredentialStatusInput.Signed>(store.readStatusInput("a"))
                    if (next == restricted) {
                        assertNull(input.storedStatus?.snapshot)
                        assertNull(input.notice)
                    } else {
                        val notice = assertNotNull(input.notice)
                        assertEquals(original.version, notice.version)
                        assertTrue(notice.acknowledged)
                        val latest = foreground.snapshot().credentials.single().presentation.lastCheckedAt
                        assertEquals(latest, notice.checkedAt)
                    }

                    setList(issuer, value = 1u, cache = next)
                    foreground.refresh(mode = CredentialStatusRefreshMode.FORCE)
                    val change = foreground.snapshot().pendingNotices.single()
                    assertEquals(StatusMeaning.INVALID, change.kind)
                    assertNotEquals(original.version, change.version)
                    foreground.acknowledgeNotice(change.reference, change.version)
                }
                service(policy).use { reopened -> assertTrue(reopened.snapshot().pendingNotices.isEmpty()) }
            }
        }
    }

    @Test
    fun aWorkerCanRetainAnAcknowledgedSessionWarningWithoutShowingItAgain() = withWallet {
        val issuer = add("a", cache = "no-store", value = 2u)
        val policy = managedCredentialStatusTrustPolicy()
        service(policy).use { foreground ->
            foreground.refresh()
            val warning = foreground.snapshot().pendingNotices.single()
            foreground.acknowledgeNotice(warning.reference, warning.version)
            assertNull(store.readCredentialStatus("a"))
            assertNull(assertIs<CredentialStatusInput.Signed>(store.readStatusInput("a")).notice)

            setList(issuer, value = 2u, cache = "max-age=600")
            assertTrue(assertIs<CredentialStatusMaintenanceResult.Finished>(wake()).hasNewNotice)
            val retained = assertNotNull(assertIs<CredentialStatusInput.Signed>(store.readStatusInput("a")).notice)
            assertFalse(retained.acknowledged)

            // Local status reads can see the new evidence before Home reads the notice.
            foreground.selectedStatuses(setOf("a"))
            assertTrue(foreground.snapshot().pendingNotices.isEmpty())
            assertEquals(retained.copy(acknowledged = true), assertIs<CredentialStatusInput.Signed>(store.readStatusInput("a")).notice)
        }
        service(policy).use { reopened -> assertTrue(reopened.snapshot().pendingNotices.isEmpty()) }
    }

    @Test
    fun aRecoveryCommittedByAnotherWorkerKeepsTheNextSuspensionVisible() = withWallet {
        val issuer = add("a", cache = "no-store", value = 2u)
        service(managedCredentialStatusTrustPolicy()).use { foreground ->
            foreground.refresh()
            val first = foreground.snapshot().pendingNotices.single()
            foreground.acknowledgeNotice(first.reference, first.version)

            setList(issuer, value = 0u, cache = "max-age=0")
            assertEquals(1, assertIs<CredentialStatusMaintenanceResult.Finished>(wake()).checked)
            assertNotNull(store.readCredentialStatus("a"))
            assertNull(assertIs<CredentialStatusInput.Signed>(store.readStatusInput("a")).notice)

            // The foreground wallet reads neither the clear result nor the next suspension until here.
            setList(issuer, value = 2u, cache = "max-age=600")
            assertTrue(assertIs<CredentialStatusMaintenanceResult.Finished>(wake()).hasNewNotice)
            val next = foreground.snapshot().pendingNotices.single()
            assertEquals(StatusMeaning.SUSPENDED, next.kind)
            assertNotEquals(first.version, next.version)
        }
    }

    @Test
    fun aCustomStoreWithNoticesCanReadAndAcknowledgeThemWithoutPaging() = withWallet {
        add("a", value = 2u)
        val custom = object : WalletCredentialStore by store, CredentialStatusNoticeStore by store {}
        val policy = managedCredentialStatusTrustPolicy()
        service(policy, custom).use { foreground ->
            foreground.refresh()
            val notice = foreground.snapshot().pendingNotices.single()
            val row = assertIs<CredentialStatusInput.Signed>(custom.readStatusInput("a"))
            assertEquals(row.notice?.version, notice.version)
            foreground.acknowledgeNotice(notice.reference, notice.version)
        }
        service(policy, custom).use { assertTrue(it.snapshot().pendingNotices.isEmpty()) }
    }

    @Test
    fun aSnapshotOnlyStoreStillShowsSessionNotices() = withWallet {
        add("a", value = 2u)
        val custom = object : WalletCredentialStore by store, CredentialStatusSnapshotStore by store {}
        val policy = managedCredentialStatusTrustPolicy()
        service(policy, custom).use { foreground ->
            foreground.refresh()
            assertEquals(StatusMeaning.SUSPENDED, foreground.snapshot().pendingNotices.single().kind)
        }
        assertNotNull(store.readCredentialStatus("a"))
        assertNull(assertIs<CredentialStatusInput.Signed>(store.readStatusInput("a")).notice)
        service(policy, custom).use { assertTrue(it.snapshot().pendingNotices.isEmpty()) }
    }

    @Test
    fun dismissingTheDisplayedVersionWhileItMovesToMemoryAcknowledgesIt() = withWallet {
        val issuer = add("a", value = 2u)
        val cleared = CompletableDeferred<Unit>()
        val completeCommit = CompletableDeferred<Unit>()
        val original = store
        val storeAtHandoff = object : WalletCredentialStore by original, CredentialStatusInventory by original {
            override suspend fun compareAndSetCredentialStatusAndNotice(
                credential: StoredCredential,
                expected: CredentialStatusStoredRecord?,
                updated: CredentialStatusStoredRecord,
                transition: (CredentialStatusNoticeState?) -> CredentialStatusNoticeState?,
            ): CredentialStatusCommit {
                val commit = original.compareAndSetCredentialStatusAndNotice(credential, expected, updated, transition)
                if (commit == CredentialStatusCommit.COMMITTED && updated.snapshot == null) {
                    cleared.complete(Unit)
                    completeCommit.await()
                }
                return commit
            }
        }
        service(managedCredentialStatusTrustPolicy(), storeAtHandoff).use { foreground ->
            foreground.refresh()
            val notice = foreground.snapshot().pendingNotices.single()
            setList(issuer, value = 2u, cache = "no-store")
            coroutineScope {
                val refresh = async { foreground.refresh(mode = CredentialStatusRefreshMode.FORCE) }
                try {
                    withTimeout(5.seconds) { cleared.await() }
                    val dismiss = async(start = CoroutineStart.UNDISPATCHED) {
                        foreground.acknowledgeNotice(notice.reference, notice.version)
                    }
                    completeCommit.complete(Unit)
                    refresh.await()
                    dismiss.await()
                } finally {
                    completeCommit.complete(Unit)
                }
            }
            assertTrue(foreground.snapshot().pendingNotices.isEmpty())
        }
    }

    @Test
    fun foregroundAndWorkerOverlapCreatesOnlyOneNotice() = withWallet {
        add("a", value = 1u)
        val workerRequested = CompletableDeferred<Unit>()
        val releaseWorker = CompletableDeferred<Unit>()
        val requestCount = AtomicInteger()
        beforeResponse = { _ ->
            if (requestCount.incrementAndGet() == 1) {
                workerRequested.complete(Unit)
                releaseWorker.await()
            }
        }
        coroutineScope {
            val worker = async { wake() }
            try {
                workerRequested.await()
                service(managedCredentialStatusTrustPolicy()).use { foreground ->
                    assertEquals(CredentialStatusRefreshOutcome.COMPLETED, foreground.refresh().single().outcome)
                    val notice = foreground.snapshot().pendingNotices.single()
                    releaseWorker.complete(Unit)
                    assertFalse(assertIs<CredentialStatusMaintenanceResult.Finished>(worker.await()).hasNewNotice)
                    assertEquals(notice.version, foreground.snapshot().pendingNotices.single().version)
                }
            } finally {
                releaseWorker.complete(Unit)
            }
        }
        assertEquals(2, requestCount.get())
    }

    @Test
    fun replacementAfterTheRowReadCannotSplitTheEvidenceAndNotice() = withWallet {
        add("a", value = 2u)
        assertTrue(assertIs<CredentialStatusMaintenanceResult.Finished>(wake()).hasNewNotice)
        val policy = managedCredentialStatusTrustPolicy()
        service(policy).use { foreground ->
            assertEquals(StatusMeaning.SUSPENDED, foreground.snapshot().pendingNotices.single().kind)
        }
        val replacement = StatusTestIssuer.create("https://status.example/replacement").credential()
        val original = store
        var replaced = false
        val replacing = object : WalletCredentialStore by original, CredentialStatusInventory by original {
            override suspend fun statusPage(afterId: String?, limit: Int): List<CredentialStatusInput> {
                val page = original.statusPage(afterId, limit)
                if (!replaced) {
                    open().use { writer ->
                        val other = SqlDelightCredentialStore(WalletPersistenceDatabase(writer).walletPersistenceQueries)
                        other.addCredential(StoredCredential("a", replacement))
                    }
                    replaced = true
                }
                return page
            }
        }
        service(policy, replacing).use { foreground ->
            val before = foreground.snapshot()
            val suspension = before.pendingNotices.single()
            assertEquals(StatusMeaning.SUSPENDED, suspension.kind)
            assertEquals(CredentialStatusFreshness.FRESH, before.credentials.single().presentation.freshness)
            assertFalse(suspension.historical)

            val current = foreground.snapshot()
            assertTrue(current.pendingNotices.isEmpty())
            assertEquals(CredentialStatusFreshness.UNKNOWN, current.credentials.single().presentation.freshness)
        }
        assertEquals(replacement.signed, store.getCredential("a")?.credential?.signed)
        assertNull(store.readCredentialStatus("a"))
        assertNull(assertIs<CredentialStatusInput.Signed>(store.readStatusInput("a")).notice)
    }

    @Test
    fun cancellationWhileReadingALaterRowPreservesThePreviousCommitAndCursor() = withWallet {
        val first = add("a", value = 2u)
        val second = add("b")
        val grant = requireNotNull(SqlDelightStatusMaintenanceStore(queries, "review").read()?.grantId)
        val readingSecond = CompletableDeferred<Unit>()

        suspend fun scan(interruptSecond: Boolean) {
            open().use { driver ->
                val session = requireNotNull(SqlDelightStatusMaintenanceStore.admit(driver, "review", grant))
                if (!interruptSecond) assertEquals("a", session.cursor)
                val inventory = object : CredentialStatusInventory by session.inventory {
                    override suspend fun statusPage(afterId: String?, limit: Int): List<CredentialStatusInput> {
                        val rows = session.inventory.statusPage(afterId, limit)
                        if (interruptSecond && rows.any { it.id == "b" }) {
                            readingSecond.complete(Unit)
                            awaitCancellation()
                        }
                        return rows
                    }
                }
                WalletCredentialStatusService.refreshMaintenancePage(inventory, session.cursor, 2, client,
                    ::managedCredentialStatusTrustPolicy, session::advance, onItem = {}, onNoticeCommitted = {})
            }
        }

        withTimeout(10.seconds) {
            val worker = launch { scan(interruptSecond = true) }
            readingSecond.await()
            worker.cancelAndJoin()
        }
        assertNotNull(store.readCredentialStatus("a"))
        assertNull(store.readCredentialStatus("b"))
        scan(interruptSecond = false)
        assertNotNull(store.readCredentialStatus("b"))
        assertEquals(listOf(first.uri, second.uri), requests)
    }

    @Test
    fun budgetExpiryReportsCommittedNoticeAndDefersTheInterruptedCredential() = withWallet {
        val suspended = add("a", value = 2u)
        val delayed = add("b")
        beforeResponse = { uri -> if (uri == delayed.uri) awaitCancellation() }

        val result = wake(budget = 2.seconds)
        assertEquals(CredentialStatusMaintenanceResult.Finished(1, 0, 1, hasNewNotice = true), result)
        assertEquals(listOf(suspended.uri, delayed.uri), requests)
        service(managedCredentialStatusTrustPolicy()).use { foreground ->
            val notice = foreground.snapshot().pendingNotices.single()
            assertEquals("a", notice.reference.credentialId)
            assertEquals(StatusMeaning.SUSPENDED, notice.kind)
            assertFalse(notice.historical)

            beforeResponse = {}
            assertFalse(assertIs<CredentialStatusMaintenanceResult.Finished>(wake()).hasNewNotice)
            assertEquals(notice.version, foreground.snapshot().pendingNotices.single().version)
        }
    }

    @Test
    fun retiringTheGrantDuringAPartialPassSuppressesItsAlertSignal() = withWallet {
        add("a", value = 2u)
        val second = add("b")
        val requested = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        beforeResponse = { uri ->
            if (uri == second.uri) {
                requested.complete(Unit)
                resume.await()
            }
        }
        coroutineScope {
            val worker = async { wake() }
            try {
                requested.await()
                enrollment.reconcile(automaticChecks = false, resumePaused = false)
            } finally {
                resume.complete(Unit)
            }
            assertEquals(CredentialStatusMaintenanceResult.Ineligible, worker.await())
        }
        service(managedCredentialStatusTrustPolicy()).use {
            assertEquals("a", it.snapshot().pendingNotices.single().reference.credentialId)
        }
        assertNull(store.readCredentialStatus("b"))
    }

    private class Fixture(val driver: JdbcSqliteDriver, val open: () -> JdbcSqliteDriver) {
        val queries = WalletPersistenceDatabase(driver).walletPersistenceQueries
        val store = SqlDelightCredentialStore(queries)
        val enrollment = StatusMaintenanceEnrollmentStore("review", queries) { "database" }
        private val binding = requireNotNull(enrollment.set(CredentialStatusMaintenancePreference.ENABLED)?.binding)
        val requests = mutableListOf<String>()
        private val responses = mutableMapOf<String, Pair<String, String>>()
        var beforeResponse: suspend (String) -> Unit = {}
        val client = HttpClient(MockEngine { request ->
            val uri = request.url.toString()
            requests += uri
            val (list, cache) = responses.getValue(uri)
            beforeResponse(uri)
            respond(list, headers = headersOf(
                HttpHeaders.ContentType to listOf("application/statuslist+jwt"),
                HttpHeaders.CacheControl to listOf(cache),
            ))
        })

        suspend fun add(id: String, cache: String = "max-age=600", value: UInt = 0u): StatusTestIssuer {
            val issuer = StatusTestIssuer.create("https://status.example/$id")
            store.addCredential(StoredCredential(id, issuer.credential()))
            setList(issuer, value, cache)
            return issuer
        }

        suspend fun setList(issuer: StatusTestIssuer, value: UInt, cache: String = "max-age=600") {
            responses[issuer.uri] = issuer.list(value = value, ttl = 600, bits = 2) to cache
        }

        fun service(policy: String? = null, credentials: WalletCredentialStore = store) =
            WalletCredentialStatusService(Wallet("review", credentialStores = listOf(credentials)), client, trustPolicyId = policy)

        suspend fun wake(maxItems: Int = 25, budget: Duration = 10.seconds) =
            runCredentialStatusMaintenance(binding, maxItems, budget, open = { open() }, httpClient = client)
    }

    private fun withWallet(test: suspend Fixture.() -> Unit) = runBlocking {
        val path = Files.createTempFile("wallet-status-maintenance", ".sqlite")
        try {
            val open = { JdbcSqliteDriver("jdbc:sqlite:$path") }
            open().use { driver ->
                WalletPersistenceDatabase.Schema.create(driver)
                val fixture = Fixture(driver, open)
                fixture.client.use { fixture.test() }
            }
        } finally {
            Files.deleteIfExists(path)
        }
    }
}
