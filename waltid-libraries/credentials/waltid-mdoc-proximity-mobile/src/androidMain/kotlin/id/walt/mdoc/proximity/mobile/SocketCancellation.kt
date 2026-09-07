package id.walt.mdoc.proximity.mobile

import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/** Owns closure across operations, including a pending native call that ignores thread interruption. */
internal class BlockingSocket<T : Closeable>(val socket: T) : Closeable {
    private val closed = AtomicBoolean(false)

    suspend fun <R> run(disposeResult: (R) -> Unit = {}, operation: (T) -> R): R = coroutineScope {
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { close() }
            launch(Dispatchers.IO) {
                try {
                    check(!closed.get()) { "Socket is closed" }
                    val result = operation(socket)
                    continuation.resume(result) { _, value, _ ->
                        close()
                        disposeResult(value)
                    }
                } catch (failure: Exception) {
                    close()
                    continuation.resumeWithException(failure)
                }
            }
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) runCatching { socket.close() }
    }
}
