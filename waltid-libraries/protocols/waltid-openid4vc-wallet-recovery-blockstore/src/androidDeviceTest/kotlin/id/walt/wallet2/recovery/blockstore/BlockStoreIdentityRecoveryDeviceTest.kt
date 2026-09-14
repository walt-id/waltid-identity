package id.walt.wallet2.recovery.blockstore

import androidx.test.platform.app.InstrumentationRegistry
import id.walt.wallet2.mobile.identity.IdentityRecoveryData
import id.walt.wallet2.mobile.identity.RecoveryAvailability
import id.walt.wallet2.mobile.identity.RecoveryReceipt
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.uuid.Uuid

class BlockStoreIdentityRecoveryDeviceTest {
    @Test fun localDeviceTransferRecordIsIdempotentAndScoped() = runTest {
        val provider = BlockStoreIdentityRecovery(InstrumentationRegistry.getInstrumentation().targetContext,
            "wal749-test-${Uuid.random()}", BlockStoreRecoveryMode.DeviceTransfer)
        assertIs<RecoveryAvailability.Available>(provider.availability())
        val id = Uuid.random().toString()
        val bytes = "synthetic recovery adapter fixture".encodeToByteArray()
        try {
            assertEquals(RecoveryReceipt.AcceptedLocally, provider.store(id, IdentityRecoveryData(bytes)))
            assertEquals(RecoveryReceipt.AcceptedLocally, provider.store(id, IdentityRecoveryData(bytes)))
            assertEquals(listOf(id), provider.list())
            assertContentEquals(bytes, assertNotNull(provider.retrieve(id)).copyBytes())
            assertFailsWith<IllegalStateException> { provider.store(id, IdentityRecoveryData(byteArrayOf(1))) }
        } finally { provider.delete(id) }
        assertNull(provider.retrieve(id))
        assertTrue(provider.list().isEmpty())
    }
}
