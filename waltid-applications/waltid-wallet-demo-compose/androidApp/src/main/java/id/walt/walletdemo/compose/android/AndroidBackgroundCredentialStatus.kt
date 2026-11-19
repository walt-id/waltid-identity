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
import id.walt.wallet2.mobile.CredentialStatusMaintenanceResult
import id.walt.wallet2.mobile.refreshDueStatusesForExistingWallet
import id.walt.walletdemo.compose.logic.createAndroidDemoSharingSettingsStore
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
            if (previous != binding) {
                check(preferences.edit().putString(BINDING, binding).commit()) { "Could not save background status enrollment" }
            }
            val workManager = WorkManager.getInstance(context)
            if (previous != binding || binding == null) workManager.cancelUniqueWork(WORK).await()
            if (binding != null) {
                val request = PeriodicWorkRequestBuilder<CredentialStatusWorker>(1, TimeUnit.HOURS)
                    .setInitialDelay(1, TimeUnit.HOURS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
                workManager.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request).await()
            }
        }
    }

    fun notifyLocalRead() { changes.tryEmit(Unit) }
}

/** WorkManager instantiates this with only the application context and a constant task name. */
class CredentialStatusWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val binding = AndroidBackgroundCredentialStatus.binding(applicationContext) ?: return Result.success()
        return try {
            val result = refreshDueStatusesForExistingWallet(applicationContext, binding)
            if (result != CredentialStatusMaintenanceResult.Ineligible) AndroidBackgroundCredentialStatus.notifyLocalRead()
            // This periodic request supplies the next opportunity even after transient storage or budget deferral.
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.failure()
        }
    }
}
