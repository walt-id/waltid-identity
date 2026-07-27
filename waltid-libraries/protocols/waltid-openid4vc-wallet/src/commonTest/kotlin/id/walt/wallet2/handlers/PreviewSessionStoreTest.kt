package id.walt.wallet2.handlers

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class PreviewSessionStoreTest {
    @Test
    fun defaultIdsAreOpaqueAndUnpredictable() = runTest {
        val store = PreviewSessionStore<String>(sessionName = "Test")

        val first = store.create(WALLET_A, "a")
        val second = store.create(WALLET_A, "b")

        assertNotEquals(first, second)
        assertTrue(first.length >= 43)
        assertTrue(second.length >= 43)
    }

    @Test
    fun sameSourceValuesRemainIndependentlyBound() = runTest {
        val ids = ArrayDeque(listOf("first", "second"))
        val store = store(idGenerator = { ids.removeFirst() })

        val first = store.create(WALLET_A, "resolution-a")
        val second = store.create(WALLET_A, "resolution-b")

        assertEquals("resolution-a", store.consume(WALLET_A, first))
        assertEquals("resolution-b", store.consume(WALLET_A, second))
    }

    @Test
    fun crossWalletUnknownExpiredDiscardedAndConsumedFailClearly() = runTest {
        var currentTime = START
        val ids = ArrayDeque(listOf("cross", "expired", "discarded", "consumed"))
        val store = store(now = { currentTime }, idGenerator = { ids.removeFirst() })

        val cross = store.create(WALLET_A, "cross")
        assertFailure(PreviewSessionFailureReason.WRONG_WALLET) {
            store.consume(WALLET_B, cross)
        }
        assertFailure(PreviewSessionFailureReason.UNKNOWN) {
            store.consume(WALLET_A, "unknown")
        }

        val expired = store.create(WALLET_A, "expired")
        currentTime += 2.minutes
        assertFailure(PreviewSessionFailureReason.EXPIRED) {
            store.consume(WALLET_A, expired)
        }

        val discarded = store.create(WALLET_A, "discarded")
        store.discard(WALLET_A, discarded)
        assertFailure(PreviewSessionFailureReason.DISCARDED) {
            store.consume(WALLET_A, discarded)
        }

        val consumed = store.create(WALLET_A, "consumed")
        assertEquals("consumed", store.consume(WALLET_A, consumed))
        assertFailure(PreviewSessionFailureReason.CONSUMED) {
            store.consume(WALLET_A, consumed)
        }
    }

    @Test
    fun failedUseRetainsSessionAndSuccessfulUseConsumesIt() = runTest {
        val store = store(idGenerator = { "retry" })
        val id = store.create(WALLET_A, "resolution")

        assertFailsWith<ExpectedFailure> {
            store.useRetainingOnFailure(WALLET_A, id) { throw ExpectedFailure() }
        }
        assertEquals(listOf(id), store.activeIds())

        assertEquals("ok", store.useRetainingOnFailure(WALLET_A, id) { "ok" })
        assertFailure(PreviewSessionFailureReason.CONSUMED) {
            store.consume(WALLET_A, id)
        }
    }

    @Test
    fun concurrentRetryUseIsRejectedWithoutDuplicatingWork() = runTest {
        val store = store(idGenerator = { "concurrent" })
        val id = store.create(WALLET_A, "resolution")
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        val first = async {
            store.useRetainingOnFailure(WALLET_A, id) {
                started.complete(Unit)
                release.await()
            }
        }
        started.await()
        assertFailure(PreviewSessionFailureReason.IN_USE) {
            store.useRetainingOnFailure(WALLET_A, id) { }
        }
        assertFailure(PreviewSessionFailureReason.IN_USE) {
            store.clearWallet(WALLET_A)
        }
        release.complete(Unit)
        first.await()
    }

    @Test
    fun discardDuringFailedRetryRemovesSessionAfterTheAttemptReleasesIt() = runTest {
        val store = store(idGenerator = { "discard-during-use" })
        val id = store.create(WALLET_A, "resolution")
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        val attempt = async {
            assertFailsWith<ExpectedFailure> {
                store.useRetainingOnFailure(WALLET_A, id) {
                    started.complete(Unit)
                    release.await()
                    throw ExpectedFailure()
                }
            }
        }
        started.await()
        store.discard(WALLET_A, id)
        release.complete(Unit)
        attempt.await()

        assertFailure(PreviewSessionFailureReason.DISCARDED) {
            store.consume(WALLET_A, id)
        }
    }

    @Test
    fun cancellationReleasesRetryLeaseWithoutConsumingSession() = runTest {
        val store = store(idGenerator = { "cancelled" })
        val id = store.create(WALLET_A, "resolution")
        val started = CompletableDeferred<Unit>()
        val neverCompletes = CompletableDeferred<Unit>()

        val attempt = async {
            store.useRetainingOnFailure(WALLET_A, id) {
                started.complete(Unit)
                neverCompletes.await()
            }
        }
        started.await()
        attempt.cancelAndJoin()

        assertEquals(listOf(id), store.activeIds())
        assertEquals("retried", store.useRetainingOnFailure(WALLET_A, id) { "retried" })
    }

    @Test
    fun capacityEvictsOldestAvailableEntryDeterministically() = runTest {
        val ids = ArrayDeque(listOf("first", "second", "third"))
        val store = store(capacity = 2, idGenerator = { ids.removeFirst() })

        val first = store.create(WALLET_A, "a")
        val second = store.create(WALLET_A, "b")
        val third = store.create(WALLET_A, "c")

        assertEquals(listOf(second, third), store.activeIds())
        assertFailure(PreviewSessionFailureReason.EVICTED) {
            store.consume(WALLET_A, first)
        }
    }

    @Test
    fun walletClearRemovesOnlyOwnedSessionsAndTombstones() = runTest {
        val ids = ArrayDeque(listOf("a", "b", "discarded"))
        val store = store(idGenerator = { ids.removeFirst() })
        val a = store.create(WALLET_A, "a")
        val b = store.create(WALLET_B, "b")
        val discarded = store.create(WALLET_A, "discarded")
        store.discard(WALLET_A, discarded)

        store.clearWallet(WALLET_A)

        assertFailure(PreviewSessionFailureReason.UNKNOWN) {
            store.consume(WALLET_A, a)
        }
        assertFailure(PreviewSessionFailureReason.UNKNOWN) {
            store.consume(WALLET_A, discarded)
        }
        assertEquals("b", store.consume(WALLET_B, b))
    }

    private fun store(
        capacity: Int = 16,
        now: () -> Instant = { START },
        idGenerator: () -> String,
    ): PreviewSessionStore<String> = PreviewSessionStore(
        sessionName = "Test",
        capacity = capacity,
        timeToLive = 1.minutes,
        now = now,
        idGenerator = idGenerator,
    )

    private suspend fun assertFailure(
        reason: PreviewSessionFailureReason,
        block: suspend () -> Unit,
    ) {
        val error = assertFailsWith<PreviewSessionException> { block() }
        assertEquals(reason, error.reason)
    }

    private class ExpectedFailure : RuntimeException()

    private companion object {
        const val WALLET_A = "wallet-a"
        const val WALLET_B = "wallet-b"
        val START = Instant.parse("2026-07-21T00:00:00Z")
    }
}
