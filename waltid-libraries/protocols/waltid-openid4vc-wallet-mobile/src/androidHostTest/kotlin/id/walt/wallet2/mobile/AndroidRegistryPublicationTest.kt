package id.walt.wallet2.mobile

import androidx.credentials.CredentialManagerCallback
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidRegistryPublicationTest {
    @Test
    fun cancellationReturnsPromptlyButNextWriteWaitsForTheActualCallback() = runTest {
        val gate = AndroidRegistryPublication()
        lateinit var callback: CredentialManagerCallback<String, Exception>
        val old = async { gate.publish<String, Exception> { callback = it } }
        runCurrent()
        old.cancelAndJoin()
        val writes = mutableListOf<String>()
        val next = async { gate.publish<String, Exception> {
            writes += "new"
            it.onResult("new")
        } }
        runCurrent()
        assertFalse(next.isCompleted)
        writes += "old"
        callback.onResult("old")
        assertEquals("new", next.await())
        assertEquals(listOf("old", "new"), writes)
        // Duplicate completion must not release the next request's gate or resume twice.
        callback.onError(IllegalStateException("late duplicate"))
        assertEquals("third", gate.publish<String, Exception> { it.onResult("third") })
    }

    @Test
    fun synchronousFailureAndErrorCallbackReleaseTheGate() = runTest {
        val gate = AndroidRegistryPublication()
        assertFailsWith<IllegalStateException> {
            gate.publish<String, Exception> { error("submission failed") }
        }
        assertFailsWith<IllegalArgumentException> {
            gate.publish<String, Exception> { it.onError(IllegalArgumentException("provider failed")) }
        }
        assertEquals("ok", gate.publish<String, Exception> { it.onResult("ok") })
    }

    @Test
    fun cancelledWaiterNeverSubmitsOrReleasesAnotherRequestsGate() = runTest {
        val gate = AndroidRegistryPublication()
        lateinit var callback: CredentialManagerCallback<String, Exception>
        val first = async { gate.publish<String, Exception> { callback = it } }
        runCurrent()
        val cancelled = async { gate.publish<String, Exception> { error("must not submit") } }
        runCurrent()
        cancelled.cancelAndJoin()
        val next = async { gate.publish<String, Exception> { it.onResult("new") } }
        runCurrent()
        assertFalse(next.isCompleted)
        callback.onResult("first")
        assertEquals("first", first.await())
        assertEquals("new", next.await())
    }
}
