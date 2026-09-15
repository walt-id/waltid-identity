package id.walt.wallet2.mobile.test

import androidx.test.platform.app.InstrumentationRegistry
import id.walt.crypto2.keys.KeyOrigin
import id.walt.wallet2.mobile.MobileWalletConfig
import id.walt.wallet2.mobile.MobileWalletFactory
import id.walt.wallet2.mobile.identity.*
import id.walt.wallet2.persistence.keys.KeyUseAuthorizationPolicy
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.uuid.Uuid

class WalletIdentityRecoveryDeviceTest {
    @Test fun hardwareImportAndSoftwareRecoverySurviveEncryptedDatabaseReopen() = runTest {
        val factory = MobileWalletFactory(InstrumentationRegistry.getInstrumentation().targetContext)
        for (storage in listOf(IdentityKeyStorage.Hardware, IdentityKeyStorage.EncryptedDatabase)) {
            val provider = LocalRecoveryFixture()
            fun configuration() = MobileWalletConfig(walletId = "wal749-test-${Uuid.random()}",
                identity = IdentityConfiguration(recoveryProviders = listOf(provider),
                    authorization = IdentityAuthorization.Explicit(KeyUseAuthorizationPolicy.None)))
            val originalConfiguration = configuration()
            val original = factory.create(originalConfiguration)
            val destination = factory.create(configuration())
            var destinationDeleted = false
            try {
                val options = assertIs<IdentityOptions.Available>(original.identities.creationOptions(IdentityIntent.Recoverable))
                val selected = (listOf(options.recommended) + options.alternatives).singleOrNull { it.storage == storage }
                if (selected == null && storage == IdentityKeyStorage.Hardware) {
                    println("Hardware recovery unavailable; software recovery is tested independently")
                    continue
                }
                requireNotNull(selected)
                val created = assertIs<IdentityOperationResult.Active>(original.identities.create(selected)).identity
                println("Identity destination=$storage security=${created.keyFacts.securityLevel} origin=${created.keyFacts.origin}")
                if (storage == IdentityKeyStorage.Hardware) assertEquals(KeyOrigin.IMPORTED, created.keyFacts.origin)
                val reopened = factory.create(originalConfiguration)
                assertEquals(created, assertIs<WalletIdentityState.Active>(reopened.identities.state()).identity)
                val candidate = destination.identities.recoveryCandidates().single()
                val restore = destination.identities.restorationOptions(candidate).single { it.storage == storage }
                val restored = assertIs<IdentityOperationResult.Active>(destination.identities.restore(restore)).identity
                assertEquals(created.id, restored.id)
                assertEquals(created.keyId, restored.keyId)
                assertEquals(created.did, restored.did)
                assertEquals(created.publicJwk, restored.publicJwk)
                destination.deleteWallet()
                destinationDeleted = true
                assertIs<WalletIdentityState.Active>(original.identities.state())
            } finally {
                if (!destinationDeleted) destination.deleteWallet()
                original.deleteWallet()
            }
        }
    }
}

/** Synthetic, process-local transport: exercises replacement without sending secrets to a cloud service. */
private class LocalRecoveryFixture : IdentityRecoveryProvider {
    override val id = "local-device-test"
    override val displayName = "Local device test"
    private val records = mutableMapOf<String, ByteArray>()
    override suspend fun availability() = RecoveryAvailability.Available(RecoveryProtection.OperatingSystemProtected, RecoveryScope.Custom)
    override suspend fun list() = records.keys.toList()
    override suspend fun store(recordId: String, record: IdentityRecoveryData): RecoveryReceipt {
        val bytes = record.copyBytes()
        check(records[recordId]?.contentEquals(bytes) != false)
        records[recordId] = bytes
        return RecoveryReceipt.AcceptedLocally
    }
    override suspend fun retrieve(recordId: String) = records[recordId]?.let(::IdentityRecoveryData)
    override suspend fun delete(recordId: String): RecoveryReceipt { records.remove(recordId); return RecoveryReceipt.AcceptedLocally }
}
