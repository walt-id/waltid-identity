package id.walt.wallet2.mobile

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import id.walt.credentials.status.StatusTestIssuer
import id.walt.credentials.status.entries
import id.walt.credentials.status.lastObservation
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.persistence.db.WalletPersistenceDatabase
import id.walt.wallet2.persistence.stores.SqlDelightCredentialStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
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
import kotlin.time.Duration.Companion.seconds

class MobileCredentialStatusMaintenanceTest {
    @Test
    fun anExistingGrantCanRefreshAndLeaveADurableObservation() = withWallet { wallet ->
        assertEquals(1, assertIs<CredentialStatusMaintenanceResult.Finished>(wallet.refresh()).checked)
        val record = assertNotNull(wallet.store.readCredentialStatusRecord("credential"))
        assertNotNull(assertNotNull(record.snapshot).metadata.entries.single().lastObservation())
        assertTrue(wallet.closed.get())
    }

    @Test
    fun revocationDuringIssuerIoFencesTheOldHandleAndResumeRotatesTheGrant() = withWallet { wallet ->
        val requested = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        val work = async {
            wallet.refresh {
                requested.complete(Unit)
                resume.await()
            }
        }
        requested.await()
        assertEquals(CredentialStatusMaintenancePreference.PAUSED,
            wallet.enrollment.set(CredentialStatusMaintenancePreference.PAUSED)?.preference)
        val reenabled = assertNotNull(wallet.enrollment.set(CredentialStatusMaintenancePreference.ENABLED))
        assertNotEquals(wallet.binding, reenabled.binding)
        resume.complete(Unit)

        assertEquals(CredentialStatusMaintenanceResult.Ineligible, work.await())
        assertNull(wallet.store.readCredentialStatusRecord("credential"))
        assertEquals(CredentialStatusMaintenanceResult.Ineligible, wallet.refresh { error("Retired binding reached issuer I/O") })
        assertTrue(wallet.closed.get())
    }

    @Test
    fun callerCancellationDrainsTheIssuerRequestBeforeClosingItsHandle() = withWallet { wallet ->
        val entered = CompletableDeferred<Unit>()
        val databaseClosedAtRequestExit = CompletableDeferred<Boolean>()
        val work = async {
            wallet.refresh(beforeSend = {
                try {
                    entered.complete(Unit)
                    awaitCancellation()
                } finally {
                    databaseClosedAtRequestExit.complete(wallet.closed.get())
                }
            })
        }
        entered.await()
        work.cancelAndJoin()

        assertTrue(databaseClosedAtRequestExit.isCompleted, "The request must finish before maintenance returns")
        assertFalse(databaseClosedAtRequestExit.await(), "The store must stay open until the request finishes")
        assertTrue(wallet.closed.get())
        assertNull(wallet.store.readCredentialStatusRecord("credential"))
    }

    private fun withWallet(test: suspend kotlinx.coroutines.CoroutineScope.(Fixture) -> Unit) = runBlocking {
        val path = Files.createTempFile("status-maintenance", ".sqlite")
        try {
            val open = { JdbcSqliteDriver("jdbc:sqlite:$path") }
            open().use { foreground ->
                WalletPersistenceDatabase.Schema.create(foreground)
                val queries = WalletPersistenceDatabase(foreground).walletPersistenceQueries
                val store = SqlDelightCredentialStore(queries)
                val issuer = StatusTestIssuer.create()
                store.addCredential(StoredCredential("credential", issuer.credential()))
                val enrollment = StatusMaintenanceEnrollmentStore("wallet", queries) { "database" }
                val binding = assertNotNull(enrollment.set(CredentialStatusMaintenancePreference.ENABLED)?.binding)
                withTimeout(12.seconds) { test(Fixture(enrollment, store, binding, issuer.list(), open)) }
            }
        } finally {
            Files.deleteIfExists(path)
        }
    }

    private class Fixture(
        val enrollment: StatusMaintenanceEnrollmentStore,
        val store: SqlDelightCredentialStore,
        val binding: String,
        private val list: String,
        private val open: () -> SqlDriver,
    ) {
        val closed = AtomicBoolean(false)

        suspend fun refresh(
            beforeSend: suspend () -> Unit = {},
            beforeResponse: suspend () -> Unit = {},
        ): CredentialStatusMaintenanceResult =
            HttpClient(MockEngine {
                beforeResponse()
                respond(list, headers = headersOf(HttpHeaders.ContentType, "application/statuslist+jwt"))
            }) {
                // This hook runs in the SDK request coroutine. The mock engine owns its own call job.
                install(createClientPlugin("MaintenanceRequestProbe") {
                    onRequest { _, _ -> beforeSend() }
                })
            }.use { client ->
                closed.set(false)
                runCredentialStatusMaintenance(binding, 1, 8.seconds, open = {
                    val driver = open()
                    object : SqlDriver by driver {
                        override fun close() {
                            driver.close()
                            closed.set(true)
                        }
                    }
                }, httpClient = client)
            }
    }
}
