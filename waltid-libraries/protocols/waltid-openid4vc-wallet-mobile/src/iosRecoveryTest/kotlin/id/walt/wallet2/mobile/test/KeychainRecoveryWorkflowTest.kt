package id.walt.wallet2.mobile.test

import id.walt.wallet2.mobile.identity.SigningIdentityKeyStorage
import id.walt.wallet2.mobile.identity.SigningIdentity
import id.walt.wallet2.persistence.encryption.IosDatabaseEncryptionKeyProvider
import id.walt.wallet2.persistence.keys.IosPlatformKeyProvider
import id.walt.wallet2.persistence.stores.DriverFactory
import id.walt.wallet2.recovery.keychain.KeychainIdentityRecovery
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import platform.Foundation.NSProcessInfo
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import id.walt.wallet2.mobile.identity.IdentityRecoveryData
import id.walt.wallet2.mobile.identity.RecoveryReceipt

/** Requires an entitled application host; never treats unavailable Keychain service as a pass. */
class KeychainRecoveryWorkflowTest {
    @Test fun exchangesRecordsWithTheSwiftAdapter() = runTest {
        val args = NSProcessInfo.processInfo.arguments.filterIsInstance<String>()
        fun argument(name: String) = args.single { it.startsWith("--$name=") }.substringAfter('=')
        val provider = KeychainIdentityRecovery(argument("interopNamespace"))
        val fromKotlin = "synthetic-parity-fixture:kotlin".encodeToByteArray()
        val fromSwift = "synthetic-parity-fixture:swift".encodeToByteArray()
        if (argument("interopPhase") == "write") {
            assertEquals(RecoveryReceipt.AcceptedLocally, provider.store("from-kotlin", IdentityRecoveryData(fromKotlin)))
        } else {
            try {
                assertContentEquals(fromSwift, provider.retrieve("from-swift")?.copyBytes())
                assertEquals(RecoveryReceipt.AcceptedLocally, provider.store("from-swift", IdentityRecoveryData(fromSwift)))
                assertContentEquals(fromKotlin, provider.retrieve("from-kotlin")?.copyBytes())
            } finally {
                provider.delete("from-kotlin")
                provider.delete("from-swift")
            }
        }
    }


    @Test fun runPhase() = runTest {
        val args = NSProcessInfo.processInfo.arguments.filterIsInstance<String>()
        fun argument(name: String) = args.single { it.startsWith("--$name=") }.substringAfter('=')
        val runId = argument("recoveryRun")
        require(runId.matches(Regex("[a-f0-9-]{36}")))
        val provider = KeychainIdentityRecovery("wal749-$runId")
        val drivers = DriverFactory()
        val workflow = RecoveryWorkflow(provider) {
            RecoveryTestWallet.open(runId, provider, IosDatabaseEncryptionKeyProvider(), IosPlatformKeyProvider(),
                drivers::createEncryptedDriver, drivers::deleteDatabase)
        }
        fun expected() = Json.decodeFromString<SigningIdentity>(Base64.decode(argument("recoveryExpected")).decodeToString())
        when (argument("recoveryPhase")) {
            "prepare" -> {
                val identity = workflow.prepare(SigningIdentityKeyStorage.valueOf(argument("recoveryStorage")))
                println("RECOVERY_CHECKPOINT=" + Base64.encode(Json.encodeToString(identity).encodeToByteArray()))
            }
            "lose-local" -> workflow.loseLocalState(expected())
            "restore" -> workflow.restore(expected(), SigningIdentityKeyStorage.valueOf(argument("recoveryStorage")))
            "verify" -> workflow.verify(expected(), SigningIdentityKeyStorage.valueOf(argument("recoveryStorage")))
            "cleanup" -> workflow.cleanup()
            else -> error("Unknown recovery phase")
        }
    }
}
