package id.walt.wallet2.mobile

import androidx.credentials.CredentialManagerCallback
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex

/** Orders external writes even when the provider does not implement cancellation. */
internal class AndroidRegistryPublication {
    private val mutex = Mutex()

    suspend fun <T, E : Exception> publish(start: (CredentialManagerCallback<T, E>) -> Unit): T {
        currentCoroutineContext().ensureActive()
        mutex.lock()
        // The submitted callback owns the lock, including after the caller has been cancelled.
        // AndroidX's Play Services provider ignores CancellationSignal; releasing on cancellation
        // would allow an old write to finish after a newer replacement or deletion.
        return suspendCancellableCoroutine { continuation ->
            val completed = AtomicBoolean()
            fun finish(result: Result<T>) {
                if (!completed.compareAndSet(false, true)) return
                mutex.unlock()
                result.fold(continuation::resume, continuation::resumeWithException)
            }
            if (!continuation.isActive) {
                mutex.unlock()
                return@suspendCancellableCoroutine
            }
            try {
                start(object : CredentialManagerCallback<T, E> {
                    override fun onResult(result: T) = finish(Result.success(result))
                    override fun onError(e: E) = finish(Result.failure(e))
                })
            } catch (error: Exception) {
                finish(Result.failure(error))
            }
        }
    }
}
