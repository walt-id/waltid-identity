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

/** Requires an entitled application host; never treats unavailable Keychain service as a pass. */
class KeychainRecoveryWorkflowTest {
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
