package id.walt.verifier2

import id.walt.verifier2.verification2.isInfrastructureFailure
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A dependency failure must not be reported to a wallet as invalid_request.
 *
 * A load test at concurrency 128 produced 1,676 responses saying the wallet's presentation was
 * malformed when the actual cause was `MongoTimeoutException: Timed out after 5087 ms while waiting
 * for a connection to server`. 400 is both wrong and terminal - it gives the wallet no reason to
 * retry a request that would have succeeded moments later.
 */
class InfrastructureFailureClassificationTest {

    /** Stand-in for the driver exception: commonMain cannot reference the MongoDB driver. */
    private class MongoTimeoutException(message: String) : Exception(message)

    private class MongoSocketReadTimeoutException(message: String) : Exception(message)

    private class MongoNotPrimaryException(message: String) : Exception(message)

    @Test
    fun storeTimeoutIsAnInfrastructureFailure() {
        assertTrue(
            MongoTimeoutException("Timed out after 5087 ms while waiting for a connection to server")
                .isInfrastructureFailure()
        )
    }

    @Test
    fun socketAndFailoverFailuresAreInfrastructureFailures() {
        assertTrue(MongoSocketReadTimeoutException("read timed out").isInfrastructureFailure())
        assertTrue(MongoNotPrimaryException("not primary").isInfrastructureFailure())
        assertTrue(IOException("connection reset").isInfrastructureFailure())
    }

    @Test
    fun wrappedTimeoutIsFoundThroughTheCauseChain() {
        // The driver's timeout reaches the engine wrapped by the store layer, so only walking the
        // chain finds it - classifying on the outermost type alone would miss every real case.
        val wrapped = IllegalStateException(
            "could not persist session",
            RuntimeException("store write failed", MongoTimeoutException("Timed out after 5000 ms"))
        )
        assertTrue(wrapped.isInfrastructureFailure())
    }

    @Test
    fun aBadPresentationIsNotAnInfrastructureFailure() {
        // Conservative on purpose: an unrecognised exception stays a rejection, so a genuine client
        // bug is not hidden behind a retry loop.
        assertFalse(IllegalArgumentException("mdoc element digest mismatch").isInfrastructureFailure())
        assertFalse(SerializationLikeException("Unexpected JSON token at offset 12").isInfrastructureFailure())
        assertFalse(Exception("Credential policy verification failed").isInfrastructureFailure())
    }

    private class SerializationLikeException(message: String) : Exception(message)

    @Test
    fun aCycleInTheCauseChainTerminates() {
        // Self-referential causes exist in the wild; without the seen-set this loops forever.
        val a = IllegalStateException("a")
        val b = IllegalStateException("b", a)
        a.initCauseCompat(b)
        assertFalse(b.isInfrastructureFailure())
    }
}

/** Kotlin has no common initCause; the JVM test only needs the cycle to be reachable. */
private fun Throwable.initCauseCompat(cause: Throwable) {
    runCatching {
        val f = this::class.java.superclass.getDeclaredField("cause")
        f.isAccessible = true
        f.set(this, cause)
    }
}
