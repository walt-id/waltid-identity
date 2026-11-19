package id.walt.wallet2.mobile

import app.cash.sqldelight.db.SqlDriver
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.persistence.db.WalletPersistenceDatabase
import id.walt.wallet2.persistence.db.WalletPersistenceQueries
import id.walt.wallet2.persistence.encryption.WalletPersistenceException
import id.walt.wallet2.persistence.stores.SqlDelightCredentialStore
import id.walt.wallet2.persistence.stores.StatusMaintenanceStorageException
import id.walt.wallet2.status.CredentialStatusRefreshOutcome
import id.walt.wallet2.status.WalletCredentialStatusService
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.TimeSource
import kotlin.uuid.Uuid

/** Foreground authorization for the managed wallet's automatic background status checks. */
public enum class CredentialStatusMaintenancePreference {
    /** Remove the opt-in and retire its current authorization. */
    OFF,
    /** Preserve an existing opt-in while retiring its active authorization. */
    PAUSED,
    /** Permit work for this wallet under its current or a new authorization. */
    ENABLED,
}

/** A foreground preference and the opaque binding an authorized host can persist privately. */
public class CredentialStatusMaintenanceEnrollment internal constructor(
    public val preference: CredentialStatusMaintenancePreference,
    public val binding: String?,
)

/** Result of one due-only pass. It contains no wallet, credential, endpoint, or key identifiers. */
public sealed interface CredentialStatusMaintenanceResult {
    /** Counts of completed attempts, unchanged entries, and work left for a later pass. */
    public data class Finished(public val checked: Int, public val unchanged: Int, public val remaining: Int) :
        CredentialStatusMaintenanceResult

    /** A transient platform/storage failure or the end of the operation's execution budget. */
    public data object Deferred : CredentialStatusMaintenanceResult

    /** The granted wallet is missing, incompatible, or no longer authorized under this binding. */
    public data object Ineligible : CredentialStatusMaintenanceResult
}

@Serializable
internal data class StatusMaintenanceBinding(val walletId: String, val location: String, val grantId: String) {
    val databaseName: String get() = "wallet_$walletId"
}

internal class StatusMaintenanceEnrollmentStore(
    private val walletId: String,
    private val queries: WalletPersistenceQueries,
    private val location: () -> String?,
) {
    fun retire() { queries.deleteCredentialStatusMaintenance(walletId) }

    fun get(): CredentialStatusMaintenanceEnrollment? {
        if (!isValidMaintenanceWalletId(walletId)) return null
        val resolved = location() ?: return null
        val record = queries.selectCredentialStatusMaintenance(walletId).executeAsOneOrNull()
        val preference = when {
            record == null -> CredentialStatusMaintenancePreference.OFF
            record.grant_id == null -> CredentialStatusMaintenancePreference.PAUSED
            else -> CredentialStatusMaintenancePreference.ENABLED
        }
        val binding = record?.grant_id?.let { Json.encodeToString(StatusMaintenanceBinding(walletId, resolved, it)) }
        return CredentialStatusMaintenanceEnrollment(preference, binding)
    }

    fun set(preference: CredentialStatusMaintenancePreference): CredentialStatusMaintenanceEnrollment? {
        if (preference == CredentialStatusMaintenancePreference.OFF) {
            retire()
            return get()
        }
        // Pausing is always possible, even if the currently open wallet's location became ambiguous.
        if (preference == CredentialStatusMaintenancePreference.PAUSED) {
            queries.transaction {
                if (queries.selectCredentialStatusMaintenance(walletId).executeAsOneOrNull() != null) {
                    queries.setCredentialStatusMaintenance(walletId, null)
                }
            }
            return get()
        }
        if (!isValidMaintenanceWalletId(walletId) || location() == null) return null
        queries.transaction {
            if (queries.selectCredentialStatusMaintenance(walletId).executeAsOneOrNull()?.grant_id == null) {
                queries.setCredentialStatusMaintenance(walletId, Uuid.random().toString())
            }
        }
        return get()
    }
}

private fun isValidMaintenanceWalletId(id: String): Boolean =
    id.isNotBlank() && id.none { it == '/' || it == '\\' || it == '\u0000' }

internal suspend fun runCredentialStatusMaintenance(
    binding: String,
    maxItems: Int,
    budget: Duration,
    open: (StatusMaintenanceBinding) -> SqlDriver?,
    httpClient: HttpClient? = null,
): CredentialStatusMaintenanceResult {
    require(maxItems in 1..1000 && budget.isFinite() && budget.isPositive())
    val authorized = try {
        Json.decodeFromString<StatusMaintenanceBinding>(binding)
    } catch (_: SerializationException) {
        return CredentialStatusMaintenanceResult.Ineligible
    }
    if (!isValidMaintenanceWalletId(authorized.walletId) || authorized.grantId.isBlank()) {
        return CredentialStatusMaintenanceResult.Ineligible
    }
    val started = TimeSource.Monotonic.markNow()
    return try {
        withTimeoutOrNull(budget) {
            withContext(Dispatchers.Default) {
                val driver = open(authorized) ?: return@withContext CredentialStatusMaintenanceResult.Ineligible
                try {
                    currentCoroutineContext().ensureActive()
                    val queries = WalletPersistenceDatabase(driver).walletPersistenceQueries
                    fun isAuthorized() = queries.selectCredentialStatusMaintenance(authorized.walletId)
                        .executeAsOneOrNull()?.grant_id == authorized.grantId
                    if (!isAuthorized()) return@withContext CredentialStatusMaintenanceResult.Ineligible
                    MobileDidSupport.ensureInitialized()
                    val store = SqlDelightCredentialStore.forStatusMaintenance(queries, authorized.walletId, authorized.grantId)
                    val wallet = Wallet(authorized.walletId, credentialStores = listOf(store))
                    // Joining this scope before closing the driver drains independently shared status flights.
                    val results = coroutineScope {
                        WalletCredentialStatusService(wallet, client = httpClient, parentScope = this).use { service ->
                            val remaining = budget - started.elapsedNow()
                            if (!remaining.isPositive()) return@coroutineScope null
                            service.refresh(maxItems = maxItems, executionBudget = remaining)
                        }
                    } ?: return@withContext CredentialStatusMaintenanceResult.Deferred
                    if (!isAuthorized()) return@withContext CredentialStatusMaintenanceResult.Ineligible
                    val checked = results.count { it.outcome == CredentialStatusRefreshOutcome.COMPLETED }
                    val remaining = results.count { it.outcome == CredentialStatusRefreshOutcome.DEFERRED }
                    CredentialStatusMaintenanceResult.Finished(checked, results.size - checked - remaining, remaining)
                } finally {
                    driver.close()
                }
            }
        } ?: CredentialStatusMaintenanceResult.Deferred
    } catch (_: WalletPersistenceException) {
        CredentialStatusMaintenanceResult.Deferred
    } catch (_: StatusMaintenanceStorageException) {
        CredentialStatusMaintenanceResult.Deferred
    }
}
