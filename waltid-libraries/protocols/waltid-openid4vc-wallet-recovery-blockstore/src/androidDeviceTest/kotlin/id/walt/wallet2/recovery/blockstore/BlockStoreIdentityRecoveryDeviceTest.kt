package id.walt.wallet2.recovery.blockstore

import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.auth.blockstore.BlockstoreClient
import id.walt.wallet2.mobile.identity.IdentityRecoveryData
import id.walt.wallet2.mobile.identity.IdentityProviderException
import id.walt.wallet2.mobile.identity.IdentityProviderFailure
import id.walt.wallet2.mobile.identity.RecoveryAvailability
import id.walt.wallet2.mobile.identity.RecoveryReceipt
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.uuid.Uuid

class BlockStoreIdentityRecoveryDeviceTest {
    @Test fun namespaceIsolationAndCombinedEntrySizeLimitPreserveExistingData() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val provider = BlockStoreIdentityRecovery(context, "size-${Uuid.random()}", BlockStoreRecoveryMode.DeviceTransfer)
        val isolated = BlockStoreIdentityRecovery(context, "other-${Uuid.random()}", BlockStoreRecoveryMode.DeviceTransfer)
        val id = "entry"
        val keyBytes = "${provider.id}/$id".encodeToByteArray().size
        val data = ByteArray(BlockstoreClient.MAX_SIZE - keyBytes) { 42 }
        try {
            assertIs<RecoveryAvailability.Available>(provider.availability())
            assertEquals(RecoveryReceipt.AcceptedLocally, provider.store(id, IdentityRecoveryData(data)))
            assertNull(isolated.retrieve(id))
            assertTrue(isolated.list().isEmpty())
            assertEquals(RecoveryReceipt.AcceptedLocally, isolated.delete(id))
            assertContentEquals(data, assertNotNull(provider.retrieve(id)).copyBytes())
            val oversizedId = "overflow"
            val tooLarge = ByteArray(BlockstoreClient.MAX_SIZE - "${provider.id}/$oversizedId".encodeToByteArray().size + 1)
            assertFailsWith<IllegalArgumentException> { provider.store(oversizedId, IdentityRecoveryData(tooLarge)) }
            assertNull(provider.retrieve(oversizedId))
            assertContentEquals(data, assertNotNull(provider.retrieve(id)).copyBytes())
        } finally {
            provider.delete(id)
            provider.delete("overflow")
        }
    }

    @Test fun entryQuotaFailurePreservesExistingRecordsAndDeletionReleasesCapacity() = runTest {
        val provider = BlockStoreIdentityRecovery(InstrumentationRegistry.getInstrumentation().targetContext,
            "quota-${Uuid.random()}", BlockStoreRecoveryMode.DeviceTransfer)
        val data = byteArrayOf(42)
        val ids = (0..BlockstoreClient.MAX_ENTRY_COUNT).map { "entry-$it" }
        try {
            assertIs<RecoveryAvailability.Available>(provider.availability())
            for (id in ids.dropLast(1)) provider.store(id, IdentityRecoveryData(data))
            val rejected = assertFailsWith<IdentityProviderException> {
                provider.store(ids.last(), IdentityRecoveryData(data))
            }
            assertEquals(IdentityProviderFailure.Rejected, rejected.failure)
            for (id in ids.dropLast(1)) assertContentEquals(data, assertNotNull(provider.retrieve(id)).copyBytes())
            assertNull(provider.retrieve(ids.last()))
            provider.delete(ids.first())
            assertEquals(RecoveryReceipt.AcceptedLocally, provider.store(ids.last(), IdentityRecoveryData(data)))
        } finally { ids.forEach { provider.delete(it) } }
        assertTrue(provider.list().isEmpty())
    }

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
            assertEquals(IdentityProviderFailure.Conflict,
                assertFailsWith<IdentityProviderException> { provider.store(id, IdentityRecoveryData(byteArrayOf(1))) }.failure)
        } finally { provider.delete(id) }
        assertNull(provider.retrieve(id))
        assertTrue(provider.list().isEmpty())
    }
}
