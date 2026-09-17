package id.walt.issuer2.service.openid4vci

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeferredCredentialTransactionStoreTest {

    @Test
    fun `store persists and retrieves a transaction`() = runTest {
        val store = InMemoryDeferredCredentialTransactionStore()
        val tx = DeferredCredentialTransaction(
            transactionId = "tx-1",
            sessionId = "session-1",
        )

        val saved = store.save(tx)
        val loaded = store.get("tx-1")

        assertEquals(tx, saved)
        assertEquals(tx, loaded)
    }

    @Test
    fun `consume marks a transaction as consumed exactly once`() = runTest {
        val store = InMemoryDeferredCredentialTransactionStore()
        val tx = DeferredCredentialTransaction(
            transactionId = "tx-2",
            sessionId = "session-2",
        )

        store.save(tx)

        val first = store.consume("tx-2")
        val second = store.consume("tx-2")

        assertEquals(DeferredCredentialTransactionStatus.CONSUMED, first?.status)
        assertNull(second)
    }

    @Test
    fun `update mutates transaction state`() = runTest {
        val store = InMemoryDeferredCredentialTransactionStore()
        val tx = DeferredCredentialTransaction(
            transactionId = "tx-3",
            sessionId = "session-3",
        )

        store.save(tx)

        val updated = store.update("tx-3") { it.copy(status = DeferredCredentialTransactionStatus.READY) }

        assertEquals(DeferredCredentialTransactionStatus.READY, updated?.status)
        assertEquals(DeferredCredentialTransactionStatus.READY, store.get("tx-3")?.status)
    }

    @Test
    fun `deferred flow service marks a transaction ready once the interval elapses`() = runTest {
        val service = DeferredFlowService()
        val tx = service.register(
            sessionId = "session-4",
            transactionId = "tx-4",
            intervalSeconds = 30L,
            createdAtEpochSeconds = 1000L,
        )

        val ready = service.markReady("tx-4", nowEpochSeconds = 1031L)

        assertEquals(DeferredCredentialTransactionStatus.PENDING, tx.status)
        assertEquals(DeferredCredentialTransactionStatus.READY, ready?.status)
    }
}
