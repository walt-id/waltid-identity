package id.walt.wallet2.mobile

import app.cash.sqldelight.db.SqlDriver
import id.walt.did.dids.DidService
import id.walt.did.dids.resolver.LocalResolver
import id.walt.wallet2.persistence.db.WalletPersistenceQueries
import id.walt.wallet2.persistence.encryption.WalletPersistenceException
import id.walt.wallet2.persistence.stores.SqlDelightStatusMaintenanceStore
import id.walt.wallet2.persistence.stores.StatusMaintenanceStorageException
import id.walt.wallet2.status.CredentialStatusRefreshOutcome
import id.walt.wallet2.status.WalletCredentialStatusService
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.TimeSource

/** Foreground authorization for the managed wallet's automatic background status checks. */
public enum class CredentialStatusMaintenancePreference {
    /** Remove the opt-in and retire its current authorization. */
    OFF,
    /** Preserve an existing opt-in while retiring its active authorization. */
    PAUSED,
    /** Permit work for this wallet under its current or a new authorization. */
    ENABLED,
}

/**
 * A foreground preference and the opaque binding an authorized host can persist privately.
 *
 * @property preference The preference committed to the managed wallet.
 * @property binding The opaque grant for the current wallet, present only when enabled.
 */
public class CredentialStatusMaintenanceEnrollment internal constructor(
    public val preference: CredentialStatusMaintenancePreference,
    public val binding: String?,
)

/** Result of one due-only pass. It contains no wallet, credential, endpoint, or key identifiers. */
public sealed interface CredentialStatusMaintenanceResult {
    /**
     * Redacted progress from one pass, including work committed before the SDK budget expired.
     *
     * @property checked Completed attempts, including attempts with per-credential errors.
     * @property unchanged Entries that required no new observation or lost a concurrent update.
     * @property remaining Entries deferred or left unexamined in this inventory rotation.
     * @property hasNewNotice Whether this pass committed a new retainable adverse change.
     */
    public data class Finished(
        public val checked: Int,
        public val unchanged: Int,
        public val remaining: Int,
        public val hasNewNotice: Boolean = false,
    ) : CredentialStatusMaintenanceResult

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
    private val storage = SqlDelightStatusMaintenanceStore(queries, walletId)

    fun retire() = storage.retire()

    fun get(): CredentialStatusMaintenanceEnrollment? {
        if (!isValidMaintenanceWalletId(walletId)) return null
        val resolved = location() ?: return null
        val record = storage.read()
        val preference = when {
            record == null -> CredentialStatusMaintenancePreference.OFF
            record.grantId == null -> CredentialStatusMaintenancePreference.PAUSED
            else -> CredentialStatusMaintenancePreference.ENABLED
        }
        val binding = record?.grantId?.let { Json.encodeToString(StatusMaintenanceBinding(walletId, resolved, it)) }
        return CredentialStatusMaintenanceEnrollment(preference, binding)
    }

    fun reconcile(automaticChecks: Boolean, resumePaused: Boolean): CredentialStatusMaintenanceEnrollment? =
        queries.transactionWithResult {
            if (!automaticChecks) storage.pause()
            val enrollment = get()
            if (automaticChecks && resumePaused && enrollment?.preference == CredentialStatusMaintenancePreference.PAUSED) {
                set(CredentialStatusMaintenancePreference.ENABLED)
            } else enrollment
        }

    fun set(preference: CredentialStatusMaintenancePreference): CredentialStatusMaintenanceEnrollment? {
        when (preference) {
            CredentialStatusMaintenancePreference.OFF -> storage.retire()
            CredentialStatusMaintenancePreference.PAUSED -> storage.pause()
            CredentialStatusMaintenancePreference.ENABLED -> {
                if (!isValidMaintenanceWalletId(walletId) || location() == null) return null
                storage.enable()
            }
        }
        return get()
    }
}

private fun isValidMaintenanceWalletId(id: String): Boolean =
    id.isNotBlank() && id.none { it == '/' || it == '\\' || it == '\u0000' }

// Bump the version if the built-in status authorization or resolver configuration changes.
internal suspend fun managedCredentialStatusTrustPolicy(): String? {
    val methods = DidService.resolverMethods.toMap()
    val resolver = methods.values.distinct().singleOrNull() as? LocalResolver ?: return null
    if (resolver.getSupportedMethods().getOrNull() != methods.keys) return null
    return "waltid.mobile.status.v1:" + methods.keys.sorted().joinToString(",")
}

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
        withContext(Dispatchers.Default) {
            val driver = open(authorized) ?: return@withContext CredentialStatusMaintenanceResult.Ineligible
            try {
                currentCoroutineContext().ensureActive()
                val session = SqlDelightStatusMaintenanceStore.admit(driver, authorized.walletId, authorized.grantId)
                    ?: return@withContext CredentialStatusMaintenanceResult.Ineligible
                var checked = 0
                var unchanged = 0
                var deferred = 0
                var itemInProgress = false
                var newNotice = false
                val completed = withTimeoutOrNull(budget - started.elapsedNow()) {
                    MobileDidSupport.ensureInitialized()
                    WalletCredentialStatusService.refreshMaintenancePage(
                        session.inventory, session.cursor, maxItems, httpClient, ::managedCredentialStatusTrustPolicy,
                        advance = { id -> session.advance(id).also { itemInProgress = it } },
                        onItem = {
                            itemInProgress = false
                            when (it) {
                                CredentialStatusRefreshOutcome.COMPLETED -> checked++
                                CredentialStatusRefreshOutcome.DEFERRED -> deferred++
                                else -> unchanged++
                            }
                        },
                        onNoticeCommitted = { newNotice = true },
                    )
                    true
                } == true
                if (!session.isAuthorized()) return@withContext CredentialStatusMaintenanceResult.Ineligible
                if (!completed && checked == 0 && unchanged == 0 && deferred == 0 && !newNotice) {
                    return@withContext CredentialStatusMaintenanceResult.Deferred
                }
                if (itemInProgress) deferred++
                CredentialStatusMaintenanceResult.Finished(checked, unchanged, deferred + session.remaining(), newNotice)
            } finally {
                driver.close()
            }
        }
    } catch (_: WalletPersistenceException) {
        CredentialStatusMaintenanceResult.Deferred
    } catch (_: StatusMaintenanceStorageException) {
        CredentialStatusMaintenanceResult.Deferred
    }
}
