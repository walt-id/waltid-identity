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

class SigningIdentityRecoveryDeviceTest {
    @Test fun hardwareImportAndSoftwareRecoverySurviveEncryptedDatabaseReopen() = runTest {
        val factory = MobileWalletFactory(InstrumentationRegistry.getInstrumentation().targetContext)
        for (storage in listOf(SigningIdentityKeyStorage.HardwareBacked, SigningIdentityKeyStorage.EncryptedDatabase)) {
            val provider = LocalRecoveryFixture()
            fun configuration() = MobileWalletConfig(walletId = "wal749-test-${Uuid.random()}",
                signingIdentity = SigningIdentityConfiguration(recoveryProviders = listOf(provider),
                    authorization = SigningIdentityAuthorization.Explicit(KeyUseAuthorizationPolicy.None)))
            val originalConfiguration = configuration()
            val original = factory.create(originalConfiguration)
            val destination = factory.create(configuration())
            var destinationDeleted = false
            try {
                val options = assertIs<SigningIdentityCreationOptions.Available>(original.signingIdentity.creationOptions(SigningIdentityIntent.Recoverable))
                val selected = (listOf(options.recommended) + options.alternatives).singleOrNull { it.storage == storage }
                if (selected == null && storage == SigningIdentityKeyStorage.HardwareBacked) {
                    println("Hardware recovery unavailable; software recovery is tested independently")
                    continue
                }
                requireNotNull(selected)
                val created = assertIs<SigningIdentityOperationResult.Active>(original.signingIdentity.create(selected)).identity
                println("Identity destination=$storage security=${created.keyFacts.securityLevel} origin=${created.keyFacts.origin}")
                if (storage == SigningIdentityKeyStorage.HardwareBacked) assertEquals(KeyOrigin.IMPORTED, created.keyFacts.origin)
                val reopened = factory.create(originalConfiguration)
                assertEquals(created, assertIs<SigningIdentityState.Active>(reopened.signingIdentity.state()).identity)
                val candidate = destination.signingIdentity.discoverRecovery().candidates.single()
                val restore = destination.signingIdentity.restorationOptions(candidate).single { it.storage == storage }
                val restored = assertIs<SigningIdentityOperationResult.Active>(destination.signingIdentity.restore(restore)).identity
                assertEquals(created.id, restored.id)
                assertEquals(created.keyId, restored.keyId)
                assertEquals(created.did, restored.did)
                assertEquals(created.publicJwk, restored.publicJwk)
                destination.deleteWallet()
                destinationDeleted = true
                assertIs<SigningIdentityState.Active>(original.signingIdentity.state())
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
