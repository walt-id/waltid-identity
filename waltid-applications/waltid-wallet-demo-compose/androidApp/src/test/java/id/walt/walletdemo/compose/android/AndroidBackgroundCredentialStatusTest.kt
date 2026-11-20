package id.walt.walletdemo.compose.android

import android.content.Context
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import id.walt.wallet2.mobile.CredentialStatusMaintenanceResult
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidBackgroundCredentialStatusTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val workManager get() = WorkManager.getInstance(context)
    private val scheduler = AndroidBackgroundCredentialStatus

    @Before
    fun initializeWorkManager() {
        if (!WorkManager.isInitialized()) WorkManager.initialize(context, Configuration.Builder().build())
    }

    @After
    fun cancelScheduledWork(): Unit = runBlocking { scheduler.reconcile(context, null) }

    @Test
    fun deferralRetainsTheScheduleAndIneligibilityRetiresIt(): Unit = runBlocking {
        val work = enqueue("retired-grant")

        scheduler.recordOutcome(context, work, "retired-grant", CredentialStatusMaintenanceResult.Deferred)
        assertEquals("retired-grant", scheduler.binding(context))
        assertEquals(WorkInfo.State.ENQUEUED, state(work))

        scheduler.recordOutcome(context, work, "retired-grant", CredentialStatusMaintenanceResult.Ineligible)
        assertNull(scheduler.binding(context))
        assertEquals(WorkInfo.State.CANCELLED, state(work))
    }

    @Test
    fun aLateIneligibleResultLeavesTheReplacementScheduled(): Unit = runBlocking {
        val retired = enqueue("old-grant")
        val replacement = enqueue("new-grant")

        scheduler.recordOutcome(context, retired, "old-grant", CredentialStatusMaintenanceResult.Ineligible)

        assertEquals("new-grant", scheduler.binding(context))
        assertEquals(WorkInfo.State.ENQUEUED, state(replacement))
    }

    @Test
    fun aDeliveryCanFinishCleanupAfterTheBindingWasRemoved(): Unit = runBlocking {
        val work = enqueue("retired-grant")
        val preferences = context.getSharedPreferences(WORK_NAME, Context.MODE_PRIVATE)
        assertTrue(preferences.edit().remove("enrollment").commit())

        scheduler.recordOutcome(context, work, null, CredentialStatusMaintenanceResult.Ineligible)

        assertEquals(WorkInfo.State.CANCELLED, state(work))
    }

    private suspend fun enqueue(binding: String): UUID {
        scheduler.reconcile(context, binding)
        return workManager.getWorkInfosForUniqueWork(WORK_NAME).get(5, TimeUnit.SECONDS)
            .single { !it.state.isFinished }.id
    }

    private fun state(work: UUID) = workManager.getWorkInfoById(work).get(5, TimeUnit.SECONDS)?.state

    private companion object {
        const val WORK_NAME = "wallet-credential-status-refresh"
    }
}
