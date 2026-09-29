package id.walt.wallet2.mobile

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Admission shared by the wallet facade and retained signing-identity manager. */
internal class MobileWalletLifecycle {
    private val mutex = Mutex()
    private val deletionMutex = Mutex()
    private var closed = false
    private var activeOperations = 0
    private var deleted = false

    suspend inline fun <T> use(block: () -> T): T {
        mutex.withLock {
            check(!closed) { "Mobile wallet is closed" }
            activeOperations++
        }
        try {
            return block()
        } finally {
            withContext(NonCancellable) { mutex.withLock { activeOperations-- } }
        }
    }

    /**
     * Busy rejection is nondestructive. Once admitted, deletion permanently closes operations,
     * survives caller cancellation, and can be retried after a cleanup failure.
     */
    suspend fun delete(cleanup: suspend () -> Unit) = deletionMutex.withLock {
        if (deleted) return@withLock
        mutex.withLock {
            check(activeOperations == 0) { "Cannot delete the mobile wallet while an operation is in progress" }
            closed = true
        }
        withContext(NonCancellable) {
            cleanup()
            deleted = true
        }
    }
}
