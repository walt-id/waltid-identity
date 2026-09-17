package id.walt.issuer2.service.openid4vci

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeferredFlowServiceTest {

    @Test
    fun `register creates a pending transaction`() = runTest {
        val service = DeferredFlowService()

        val transaction = service.register(
            sessionId = "session-1",
            transactionId = "tx-1",
            intervalSeconds = 10L,
            requestParameters = JsonObject(emptyMap()),
        )

        assertEquals("tx-1", transaction.transactionId)
        assertEquals("session-1", transaction.sessionId)
        assertEquals(10L, transaction.intervalSeconds)
        assertEquals(DeferredCredentialTransactionStatus.PENDING, transaction.status)
    }

    @Test
    fun `markReady transitions a transaction to ready after interval`() = runTest {
        val service = DeferredFlowService()
        service.register(
            sessionId = "session-2",
            transactionId = "tx-2",
            intervalSeconds = 30L,
            createdAtEpochSeconds = 100L,
        )

        val ready = service.markReady("tx-2", nowEpochSeconds = 131L)

        assertEquals(DeferredCredentialTransactionStatus.READY, ready?.status)
    }

    @Test
    fun `markReady keeps a transaction pending before interval`() = runTest {
        val service = DeferredFlowService()
        service.register(
            sessionId = "session-3",
            transactionId = "tx-3",
            intervalSeconds = 30L,
            createdAtEpochSeconds = 100L,
        )

        val pending = service.markReady("tx-3", nowEpochSeconds = 120L)

        assertEquals(DeferredCredentialTransactionStatus.PENDING, pending?.status)
    }

    @Test
    fun `consume turns a ready transaction into consumed`() = runTest {
        val service = DeferredFlowService()
        service.register(
            sessionId = "session-4",
            transactionId = "tx-4",
            intervalSeconds = 30L,
        )
        service.markReady("tx-4", nowEpochSeconds = 1000L)

        val consumed = service.consume("tx-4")

        assertEquals(DeferredCredentialTransactionStatus.CONSUMED, consumed?.status)
    }

    @Test
    fun `consume rejects already consumed transactions`() = runTest {
        val service = DeferredFlowService()
        service.register(
            sessionId = "session-5",
            transactionId = "tx-5",
            intervalSeconds = 30L,
        )
        service.markReady("tx-5", nowEpochSeconds = 1000L)
        service.consume("tx-5")

        val second = service.consume("tx-5")

        assertNull(second)
    }

    @Test
    fun `get returns the stored transaction`() = runTest {
        val service = DeferredFlowService()
        service.register(
            sessionId = "session-6",
            transactionId = "tx-6",
            intervalSeconds = 60L,
        )

        val stored = service.get("tx-6")

        assertEquals("tx-6", stored?.transactionId)
        assertEquals("session-6", stored?.sessionId)
    }
}
