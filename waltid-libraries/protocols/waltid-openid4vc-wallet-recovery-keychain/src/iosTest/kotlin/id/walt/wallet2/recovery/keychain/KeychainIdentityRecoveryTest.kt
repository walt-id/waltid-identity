package id.walt.wallet2.recovery.keychain

import id.walt.wallet2.mobile.identity.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.uuid.Uuid

/** The Swift adapter runs the same namespace, conflict, reload and deletion contract. */
class KeychainIdentityRecoveryTest {
    @Test fun compatibleAccessibilityClassesPreserveTheRecordContract() = runTest {
        for (accessibility in SynchronizableKeychainAccessibility.entries) {
            val namespace = "contract-${Uuid.random()}"
            val provider = KeychainIdentityRecovery(namespace, accessibility = accessibility)
            val isolated = KeychainIdentityRecovery("other-${Uuid.random()}")
            val recordId = "record-1"
            val data = "synthetic-parity-fixture".encodeToByteArray()
            try {
                assertEquals("keychain:$namespace", provider.id)
                val availability = provider.availability()
                if (availability is RecoveryAvailability.Unavailable) {
                    // Standalone simulator runners can lack the synchronization service. Verify that
                    // this is an explicit provider failure, never successful local/cloud submission.
                    assertTrue(availability.reason.isNotBlank())
                    assertEquals(IdentityProviderFailure.TemporarilyUnavailable,
                        assertFailsWith<IdentityProviderException> {
                            provider.store(recordId, IdentityRecoveryData(data))
                        }.failure)
                    continue
                }
                assertEquals(RecoveryReceipt.AcceptedLocally, provider.store(recordId, IdentityRecoveryData(data)))
                assertEquals(RecoveryReceipt.AcceptedLocally, provider.store(recordId, IdentityRecoveryData(data)))
                assertEquals(listOf(recordId), provider.list())
                assertNull(isolated.retrieve(recordId))
                val reopened = KeychainIdentityRecovery(namespace, accessibility = accessibility)
                assertContentEquals(data, reopened.retrieve(recordId)?.copyBytes())
                val conflict = assertFailsWith<IdentityProviderException> {
                    provider.store(recordId, IdentityRecoveryData(byteArrayOf(1)))
                }
                assertEquals(IdentityProviderFailure.Conflict, conflict.failure)
                assertContentEquals(data, reopened.retrieve(recordId)?.copyBytes())
                assertEquals(RecoveryReceipt.AcceptedLocally, provider.delete(recordId))
                assertEquals(RecoveryReceipt.AcceptedLocally, provider.delete(recordId))
                assertNull(reopened.retrieve(recordId))
            } finally { runCatching { provider.delete(recordId) } }
        }
    }
}
