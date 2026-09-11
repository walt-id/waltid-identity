package id.walt.mdoc.proximity.mobile

import java.io.Closeable
import kotlinx.coroutines.CompletableDeferred

/** Owns an Android Aware callback result before waking its coroutine, including a late result. */
internal class WifiAwareNativeResource<T : AutoCloseable> : Closeable {
    private val lock = Any()
    private val ready = CompletableDeferred<Unit>()
    private var closed = false
    private var resource: T? = null

    fun install(value: T) {
        val accepted = synchronized(lock) {
            if (closed || resource != null) false else {
                resource = value
                true
            }
        }
        if (accepted) ready.complete(Unit) else runCatching { value.close() }
    }

    suspend fun await(): T = try {
        ready.await()
        synchronized(lock) { checkNotNull(resource) { "Wi-Fi Aware resource is closed" } }
    } catch (failure: Throwable) {
        close()
        throw failure
    }

    fun fail(failure: Throwable) {
        ready.completeExceptionally(failure)
        close()
    }

    override fun close() {
        val owned = synchronized(lock) {
            if (closed) return
            closed = true
            resource.also { resource = null }
        }
        ready.cancel()
        runCatching { owned?.close() }
    }
}
