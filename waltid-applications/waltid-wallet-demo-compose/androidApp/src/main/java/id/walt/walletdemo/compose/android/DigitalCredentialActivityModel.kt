package id.walt.walletdemo.compose.android

import android.app.Activity
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.lang.ref.WeakReference

/** Owns one platform request across Activity recreation; never retains an Activity or replays work. */
internal abstract class DigitalCredentialActivityModel : ViewModel() {
    // This scope also owns bounded local SDK cleanup after ViewModel.onCleared.
    protected val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var activityReference = WeakReference<FragmentActivity>(null)
    protected fun interactionActivity(): FragmentActivity? = activityReference.get()
    private var started = false
    private var operation: Job? = null
    private var delivered = false
    protected var released = false
        private set

    data class Result(val code: Int, val data: Intent?)
    var result by mutableStateOf<Result?>(null)
        private set

    fun attach(activity: FragmentActivity) { activityReference = WeakReference(activity) }
    fun detach(activity: FragmentActivity) {
        if (activityReference.get() === activity) activityReference.clear()
    }

    fun start(intent: Intent, restored: Boolean) {
        if (started || released) return
        started = true
        // A new ViewModel with saved Activity state means process restoration, not rotation. The
        // SDK may have consumed the request already. Recovery belongs to retained SDK continuations.
        if (restored) {
            showFailure(IllegalStateException("This request was interrupted. Open the wallet to check saved and pending credentials before starting again."))
            return
        }
        perform { prepare(intent) }
    }

    protected fun perform(action: suspend () -> Unit) {
        if (result != null || released || operation?.isActive == true) return
        operation = scope.launch {
            try { action() }
            catch (cause: CancellationException) { throw cause }
            catch (cause: Exception) { if (result == null && !released) showFailure(cause) }
        }
    }

    protected suspend fun awaitCurrentOperation() { operation?.join() }

    protected fun finish(data: Intent? = null) {
        if (result != null || released) return
        result = Result(if (data == null) Activity.RESULT_CANCELED else Activity.RESULT_OK, data)
    }

    fun takeResult(): Result? = result?.takeUnless { delivered }?.also { delivered = true }

    protected abstract suspend fun prepare(intent: Intent)
    protected abstract fun showFailure(cause: Exception)
    protected abstract suspend fun releaseRequest()

    final override fun onCleared() {
        released = true
        activityReference.clear()
        operation?.cancel()
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                withContext(NonCancellable) { withTimeoutOrNull(5_000) { operation?.join(); releaseRequest() } }
            } finally { scope.cancel() }
        }
        super.onCleared()
    }
}
