package id.walt.wallet2.mobile.test

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import id.walt.wallet2.mobile.AndroidCredentialRegistryProjectionRecord
import id.walt.wallet2.mobile.AndroidCredentialRegistryProjectionField
import id.walt.wallet2.mobile.AndroidCredentialRegistryProjectionStore
import id.walt.wallet2.mobile.AndroidDigitalCredentialRegistry
import id.walt.wallet2.mobile.MobileWalletCredentialRegistryField
import id.walt.wallet2.mobile.MobileWalletCredentialRegistryRecord
import id.walt.wallet2.mobile.MobileWalletDigitalCredentialFormat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidCredentialRegistryProjectionStoreTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun projectionPersistsEncryptedMatcherDataWithoutWalletLocalIdentifiers() {
        val registryId = "registry-${UUID.randomUUID()}"
        val store = AndroidCredentialRegistryProjectionStore(context)
        val record = projectionRecord()

        try {
            store.replace(registryId, listOf(record))

            val persistedValues = context
                .getSharedPreferences("walt_digital_credential_registry_projection", Context.MODE_PRIVATE)
                .all
                .values
                .joinToString()
            assertFalse(persistedValues.contains("SensitiveAliceValue"))
            assertFalse(persistedValues.contains("wallet-private-credential-id"))

            val restored = store.readAll().single { it.registryId == registryId }
            assertEquals(1, restored.version)
            assertEquals(listOf(record), restored.records)
        } finally {
            store.clear(registryId)
        }

        assertTrue(store.readAll().none { it.registryId == registryId })
    }

    /**
     * Synchronizing to an empty desired state - what deleting a wallet does - must leave nothing
     * behind. Persisting an empty projection instead would keep its encrypted blob and its
     * AndroidKeyStore key indefinitely.
     */
    @Test
    fun registrySynchronizingToAnEmptyStateDropsThePersistedProjection() = runTest {
        val registryId = "registry-${UUID.randomUUID()}"
        val registry = AndroidDigitalCredentialRegistry(context)
        val store = AndroidCredentialRegistryProjectionStore(context)

        try {
            registry.replace(
                registryId,
                listOf(
                    MobileWalletCredentialRegistryRecord(
                        registryEntryId = "opaque-entry-id",
                        credentialId = "wallet-private-credential-id",
                        format = MobileWalletDigitalCredentialFormat.MDOC,
                        type = "org.iso.18013.5.1.mDL",
                        fields = listOf(
                            MobileWalletCredentialRegistryField(
                                path = listOf("org.iso.18013.5.1", "given_name"),
                                valueJson = "\"SensitiveAliceValue\"",
                                selectivelyDisclosable = true,
                            )
                        ),
                        displayName = "Driving licence",
                    )
                ),
            )
            assertTrue(store.readAll().any { it.registryId == registryId })

            registry.replace(registryId, emptyList())

            assertTrue(store.readAll().none { it.registryId == registryId })
        } finally {
            store.clear(registryId)
        }
    }

    private fun projectionRecord() = AndroidCredentialRegistryProjectionRecord(
        registryEntryId = "opaque-entry-id",
        format = MobileWalletDigitalCredentialFormat.MDOC,
        type = "org.iso.18013.5.1.mDL",
        fields = listOf(
            AndroidCredentialRegistryProjectionField(
                path = listOf("org.iso.18013.5.1", "given_name"),
                valueJson = "\"SensitiveAliceValue\"",
                selectivelyDisclosable = true,
            )
        ),
        displayName = "Driving licence",
    )
}
