package id.walt.wallet2.mobile

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import id.walt.credentials.status.CredentialStatusFreshness
import id.walt.credentials.status.StatusMeaning
import id.walt.credentials.status.StatusTestIssuer
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.persistence.db.WalletPersistenceDatabase
import id.walt.wallet2.persistence.stores.SqlDelightCredentialStore
import id.walt.wallet2.status.CredentialStatusRefreshMode
import id.walt.wallet2.status.CredentialStatusRefreshOutcome
import id.walt.wallet2.status.WalletCredentialStatusService
import id.walt.wallet2.status.reviewGuard
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
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
        assertTrue(store.statusNotices().isEmpty())
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
        service(policy).use { foreground ->
            assertEquals(CredentialStatusFreshness.FRESH, foreground.snapshot().credentials.single().presentation.freshness)
            assertEquals(CredentialStatusRefreshOutcome.UNCHANGED, foreground.refresh().single().outcome)
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
            val suspension = foreground.pendingNotices().single()
            assertEquals(StatusMeaning.SUSPENDED, suspension.kind)
            foreground.refresh(mode = CredentialStatusRefreshMode.FORCE)
            assertEquals(suspension.version, foreground.pendingNotices().single().version)

            setList(issuer, value = 1u)
            foreground.refresh(mode = CredentialStatusRefreshMode.FORCE)
            val invalid = foreground.pendingNotices().single()
            assertEquals(StatusMeaning.INVALID, invalid.kind)
            assertNotEquals(suspension.version, invalid.version)
            foreground.acknowledgeNotice(suspension.reference, suspension.version)
            assertEquals(invalid, foreground.pendingNotices().single())
            foreground.acknowledgeNotice(invalid.reference, invalid.version)
            assertTrue(foreground.pendingNotices().isEmpty())
        }
        service(managedCredentialStatusTrustPolicy()).use { reopened ->
            assertTrue(reopened.pendingNotices().isEmpty())
            reopened.refresh(mode = CredentialStatusRefreshMode.FORCE)
            assertTrue(reopened.pendingNotices().isEmpty())
            setList(issuer, value = 0u)
            reopened.refresh(mode = CredentialStatusRefreshMode.FORCE)
            assertTrue(store.statusNotices().isEmpty())
        }
        assertFalse(assertIs<CredentialStatusMaintenanceResult.Finished>(wake()).hasNewNotice)
    }

    @Test
    fun restrictedEvidenceCanWarnInSessionAndDoesNotReachTheNextWalletInstance() = withWallet {
        add("a", cache = "no-store", value = 1u)
        service().use { foreground ->
            foreground.refresh()
            val change = foreground.pendingNotices().single()
            assertEquals(StatusMeaning.INVALID, change.kind)
            foreground.acknowledgeNotice(change.reference, change.version)
            foreground.refresh(mode = CredentialStatusRefreshMode.FORCE)
            assertTrue(foreground.pendingNotices().isEmpty())
        }
        service().use { assertTrue(it.pendingNotices().isEmpty()) }
        assertNull(store.readCredentialStatus("a"))
        assertTrue(store.statusNotices().isEmpty())
    }

    @Test
    fun foregroundAndWorkerOverlapCreatesOnlyOneNotice() = withWallet {
        add("a", value = 1u)
        val workerRequested = CompletableDeferred<Unit>()
        val releaseWorker = CompletableDeferred<Unit>()
        val requestCount = AtomicInteger()
        beforeResponse = {
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
                    val notice = foreground.pendingNotices().single()
                    releaseWorker.complete(Unit)
                    assertFalse(assertIs<CredentialStatusMaintenanceResult.Finished>(worker.await()).hasNewNotice)
                    assertEquals(notice.version, foreground.pendingNotices().single().version)
                }
            } finally {
                releaseWorker.complete(Unit)
            }
        }
        assertEquals(2, requestCount.get())
    }

    private class Fixture(val driver: JdbcSqliteDriver, val open: () -> JdbcSqliteDriver) {
        val queries = WalletPersistenceDatabase(driver).walletPersistenceQueries
        val store = SqlDelightCredentialStore(queries)
        private val enrollment = StatusMaintenanceEnrollmentStore("review", queries) { "database" }
        private val binding = requireNotNull(enrollment.set(CredentialStatusMaintenancePreference.ENABLED)?.binding)
        val requests = mutableListOf<String>()
        private val responses = mutableMapOf<String, Pair<String, String>>()
        var beforeResponse: suspend () -> Unit = {}
        val client = HttpClient(MockEngine { request ->
            val uri = request.url.toString()
            requests += uri
            val (list, cache) = responses.getValue(uri)
            beforeResponse()
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

        fun service(policy: String? = null) =
            WalletCredentialStatusService(Wallet("review", credentialStores = listOf(store)), client, trustPolicyId = policy)

        suspend fun wake(maxItems: Int = 25) =
            runCredentialStatusMaintenance(binding, maxItems, 10.seconds, open = { open() }, httpClient = client)
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
