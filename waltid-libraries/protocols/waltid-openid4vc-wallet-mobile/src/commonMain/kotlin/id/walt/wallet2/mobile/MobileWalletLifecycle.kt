package id.walt.wallet2.mobile

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Admission shared by the wallet facade and retained signing-identity manager. */
internal class MobileWalletLifecycle {
    suspend inline fun <T> use(block: () -> T): T {
        mutex.withLock {
            check(!closed.value) { "Mobile wallet is closed" }
            activeOperations++
        }
        try {
            return block()
        } finally {
            withContext(NonCancellable) { mutex.withLock { activeOperations-- } }
        }
    }
    fun <T> observe(source: Flow<T>): Flow<T> = channelFlow {
        if (closed.value) return@channelFlow
        val collector = launch { source.collect { send(it) } }
        val closing = launch {
            closed.first { it }
            collector.cancel()
        }
        try {
            collector.join()
        } finally {
            closing.cancel()
        }
    }

    suspend fun close(cleanup: suspend () -> Unit) = deletionMutex.withLock {
        if (disposed || deleted) return@withLock
        mutex.withLock {
            check(activeOperations == 0) { "Cannot close the mobile wallet while an operation is in progress" }
            closed.value = true
        }
        withContext(NonCancellable) {
            cleanup()
            disposed = true
        }
    }

    /**
     * Busy rejection is nondestructive. Once admitted, deletion permanently closes operations,
     * survives caller cancellation, and can be retried after a cleanup failure.
     */
    suspend fun delete(admit: suspend () -> Unit = {}, cleanup: suspend () -> Unit) = deletionMutex.withLock {
        if (deleted) return@withLock
        mutex.withLock {
            check(activeOperations == 0) { "Cannot delete the mobile wallet while an operation is in progress" }
            admit()
            closed.value = true
        }
        withContext(NonCancellable) {
            cleanup()
            deleted = true
        }
    }
    private val mutex = Mutex()
    private val deletionMutex = Mutex()

    private val closed = MutableStateFlow(false)
    private var disposed = false

    private var activeOperations = 0

    private var deleted = false
}
