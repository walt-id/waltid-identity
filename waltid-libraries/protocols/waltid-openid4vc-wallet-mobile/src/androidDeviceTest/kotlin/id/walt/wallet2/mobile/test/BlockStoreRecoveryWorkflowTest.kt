package id.walt.wallet2.mobile.test

import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import id.walt.wallet2.mobile.identity.SigningIdentityKeyStorage
import id.walt.wallet2.mobile.identity.SigningIdentity
import id.walt.wallet2.persistence.encryption.AndroidDatabaseEncryptionKeyProvider
import id.walt.wallet2.persistence.keys.AndroidPlatformKeyProvider
import id.walt.wallet2.persistence.stores.DriverFactory
import id.walt.wallet2.recovery.blockstore.BlockStoreIdentityRecovery
import id.walt.wallet2.recovery.blockstore.BlockStoreRecoveryMode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assume.assumeTrue
import kotlin.io.encoding.Base64
import kotlin.test.Test

/** Opt-in phased test: the host retains only public metadata across process death/reinstall. */
class BlockStoreRecoveryWorkflowTest {
    @Test fun runPhase() = runTest {
        val args = InstrumentationRegistry.getArguments()
        val phase = args.getString("recoveryPhase")
        assumeTrue("Run through scripts/qualify-wallet-recovery.py", phase != null)
        val runId = requireNotNull(args.getString("recoveryRun"))
        require(runId.matches(Regex("[a-f0-9-]{36}")))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val provider = BlockStoreIdentityRecovery(context, "wal749-$runId", BlockStoreRecoveryMode.DeviceTransfer)
        val drivers = DriverFactory(context)
        val workflow = RecoveryWorkflow(provider) {
            RecoveryTestWallet.open(runId, provider, AndroidDatabaseEncryptionKeyProvider(context),
                AndroidPlatformKeyProvider(context), drivers::createEncryptedDriver, drivers::deleteDatabase)
        }
        fun expected() = Json.decodeFromString<SigningIdentity>(
            Base64.decode(requireNotNull(args.getString("recoveryExpected"))).decodeToString())
        when (phase) {
            "prepare" -> {
                val identity = workflow.prepare(SigningIdentityKeyStorage.valueOf(requireNotNull(args.getString("recoveryStorage"))))
                val publicCheckpoint = Base64.encode(Json.encodeToString(identity).encodeToByteArray())
                InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
                    putString("recoveryCheckpoint", publicCheckpoint)
                })
            }
            "lose-local" -> workflow.loseLocalState(expected())
            "restore" -> workflow.restore(expected(), SigningIdentityKeyStorage.valueOf(requireNotNull(args.getString("recoveryStorage"))))
            "verify" -> workflow.verify(expected(), SigningIdentityKeyStorage.valueOf(requireNotNull(args.getString("recoveryStorage"))))
            "cleanup" -> workflow.cleanup()
            else -> error("Unknown recovery phase")
        }
    }
}
