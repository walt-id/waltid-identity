package id.walt.walletdemo.compose.android

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import id.walt.wallet2.mobile.credentials.CredentialStatusMaintenanceResult
import id.walt.wallet2.mobile.credentials.refreshDueStatusesForExistingWallet
import id.walt.walletdemo.compose.logic.createAndroidDemoSharingSettingsStore
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal object AndroidBackgroundCredentialStatus {
    private const val WORK = "wallet-credential-status-refresh"
    private const val BINDING = "enrollment"
    private val scheduleMutex = Mutex()
    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val completed = changes.asSharedFlow()

    private fun preferences(context: Context) = context.getSharedPreferences(WORK, Context.MODE_PRIVATE)

    fun binding(context: Context): String? =
        if (createAndroidDemoSharingSettingsStore(context).automaticCredentialStatusChecks()) {
            preferences(context).getString(BINDING, null)
        } else null

    suspend fun reconcile(context: Context, binding: String?) = withContext(Dispatchers.IO) {
        scheduleMutex.withLock {
            val preferences = preferences(context)
            val previous = preferences.getString(BINDING, null)
            if (previous == null && binding == null) return@withLock

            val workManager = WorkManager.getInstance(context)
            if (previous != binding) {
                // Keep the old binding until cancellation completes so reconciliation can retry.
                if (previous != null) workManager.cancelUniqueWork(WORK).await()
                check(preferences.edit().putString(BINDING, binding).commit()) { "Could not save background status enrollment" }
            }
            if (binding != null) {
                val request = PeriodicWorkRequestBuilder<CredentialStatusWorker>(1, TimeUnit.HOURS)
                    .setInitialDelay(1, TimeUnit.HOURS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
                workManager.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request).await()
            }
        }
    }

    suspend fun recordOutcome(
        context: Context,
        workId: UUID,
        binding: String?,
        result: CredentialStatusMaintenanceResult,
    ) {
        if (result != CredentialStatusMaintenanceResult.Ineligible) {
            if (result is CredentialStatusMaintenanceResult.Finished && result.hasNewNotice) {
                scheduleMutex.withLock {
                    if (binding != null && binding(context) == binding) AndroidCredentialStatusNotifications.postIfAllowed(context)
                }
            }
            changes.tryEmit(Unit)
            return
        }
        withContext(Dispatchers.IO) {
            val cancellation = scheduleMutex.withLock {
                val preferences = preferences(context)
                if (preferences.getString(BINDING, null) != binding) return@withLock null
                if (binding != null) {
                    check(preferences.edit().remove(BINDING).commit()) { "Could not clear background status enrollment" }
                }
                // This work ID belongs to the delivery being retired.
                WorkManager.getInstance(context).cancelWorkById(workId)
            }
            cancellation?.await()
        }
    }
}

/** WorkManager instantiates this with only the application context and a constant task name. */
class CredentialStatusWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        return try {
            val binding = AndroidBackgroundCredentialStatus.binding(applicationContext)
            val result = binding?.let { refreshDueStatusesForExistingWallet(applicationContext, it) }
                ?: CredentialStatusMaintenanceResult.Ineligible
            AndroidBackgroundCredentialStatus.recordOutcome(applicationContext, id, binding, result)
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.failure()
        }
    }
}
