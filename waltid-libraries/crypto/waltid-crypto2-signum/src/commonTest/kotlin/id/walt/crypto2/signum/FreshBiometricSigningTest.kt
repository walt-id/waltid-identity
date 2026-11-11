package id.walt.crypto2.signum

import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.Signer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.*
import kotlin.time.TestTimeSource
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class FreshBiometricSigningTest {
    private val algorithm = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256)
    private val data = byteArrayOf(1, 2, 3)

    @Test
    fun approvalIsSingleUseAndCannotEscapeItsScope() = runTest {
        var calls = 0
        lateinit var escaped: Signer
        val result = withFreshBiometricSigner(10, Signer { _, _ -> calls++; byteArrayOf(7) }) { signer ->
            escaped = signer
            val signature = signer.sign(data, algorithm)
            assertFailsWith<SignumAuthorizationException> { signer.sign(data, algorithm) }
            signature
        }
        assertContentEquals(byteArrayOf(7), result)
        assertFailsWith<SignumAuthorizationException> { escaped.sign(data, algorithm) }
        assertEquals(1, calls)
    }

    @Test
    fun deadlineIncludesTimeBeforeSigningAndDoesNotRenewAtUse() = runTest {
        for (age in listOf(9999, 10000, 11000)) {
            val clock = TestTimeSource()
            var calls = 0
            val attempt = suspend {
                withFreshBiometricSigner(10, Signer { _, _ -> calls++; byteArrayOf(7) }, clock.markNow()) { signer ->
                    clock += age.milliseconds
                    signer.sign(data, algorithm)
                }
            }
            if (age < 10000) { attempt(); assertEquals(1, calls) }
            else { assertFailsWith<SignumAuthorizationException> { attempt() }; assertEquals(0, calls) }
        }
        val clock = TestTimeSource()
        assertFailsWith<SignumAuthorizationException> {
            withFreshBiometricSigner(10, Signer { _, _ -> clock += 10.seconds; byteArrayOf(7) }, clock.markNow()) {
                it.sign(data, algorithm)
            }
        }
        assertFailsWith<SignumAuthorizationException> {
            withFreshBiometricSigner(10, Signer { _, _ -> byteArrayOf(7) }, clock.markNow()) {
                val signature = it.sign(data, algorithm)
                clock += 10.seconds
                signature
            }
        }
    }

    @Test
    fun cancellationCannotReleaseAnUncooperativeNativeResult() = runTest {
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        lateinit var escaped: Signer
        var released = false
        val attempt = async {
            withFreshBiometricSigner(10, Signer { _, _ ->
                entered.complete(Unit)
                withContext(NonCancellable) { finish.await() }
                byteArrayOf(7)
            }) {
                escaped = it
                it.sign(data, algorithm)
            }
            released = true
        }
        entered.await()
        attempt.cancel()
        finish.complete(Unit)
        attempt.join()
        assertFalse(released)
        assertFailsWith<SignumAuthorizationException> { escaped.sign(data, algorithm) }
    }

    @Test
    fun aFailedOperationConsumesTheApprovalAndUnsignedCompletionFails() = runTest {
        var calls = 0
        assertFailsWith<SignumAuthorizationException> {
            withFreshBiometricSigner(10, Signer { _, _ -> calls++; error("Native failure") }) { signer ->
                assertFailsWith<IllegalStateException> { signer.sign(data, algorithm) }
                assertFailsWith<SignumAuthorizationException> { signer.sign(data, algorithm) }
            }
        }
        assertEquals(1, calls)
        assertFailsWith<SignumAuthorizationException> {
            withFreshBiometricSigner(10, Signer { _, _ -> error("Must not sign") }) { Unit }
        }
        lateinit var escaped: Signer
        assertFailsWith<IllegalStateException> {
            withFreshBiometricSigner(10, Signer { _, _ -> error("Must not sign") }) {
                escaped = it
                error("Payment construction failed")
            }
        }
        assertFailsWith<SignumAuthorizationException> { escaped.sign(data, algorithm) }
    }

    @Test
    fun concurrentUseConsumesOnlyOneApproval() = runTest {
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        var calls = 0
        withFreshBiometricSigner(10, Signer { _, _ ->
            calls++
            entered.complete(Unit)
            finish.await()
            byteArrayOf(7)
        }) { signer ->
            coroutineScope {
                val first = async { signer.sign(data, algorithm) }
                entered.await()
                val second = async {
                    assertFailsWith<SignumAuthorizationException> { signer.sign(data, algorithm) }
                }
                finish.complete(Unit)
                assertContentEquals(byteArrayOf(7), first.await())
                second.await()
            }
        }
        assertEquals(1, calls)
    }
}
