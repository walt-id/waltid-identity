package id.walt.walletdemo.compose.android

import id.walt.walletdemo.compose.logic.DemoWalletConfig
import id.walt.walletdemo.compose.logic.WalletDemoSigningProtection
import id.walt.walletdemo.compose.logic.WalletDemoSigningProtectionMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DemoWalletConfigurationTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() {
        context.getSharedPreferences("walt_wallet_demo_signing_protection", 0)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun optionalModeRequiresASelectionBeforeAnIndependentEntryPointBootstraps() {
        val config = DemoWalletConfig(signingProtectionMode = WalletDemoSigningProtectionMode.Optional)

        assertThrows(IllegalStateException::class.java) {
            config.selectedSigningProtection(context)
        }
    }

    @Test
    fun optionalModeUsesThePersistedSelection() {
        val config = DemoWalletConfig(signingProtectionMode = WalletDemoSigningProtectionMode.Optional)
        config.signingProtectionStore(context).save(WalletDemoSigningProtection.None)

        assertEquals(WalletDemoSigningProtection.None, config.selectedSigningProtection(context))
    }

    @Test
    fun optionalModeRejectsAnInvalidPersistedSelection() {
        context.getSharedPreferences("walt_wallet_demo_signing_protection", 0)
            .edit()
            .putString("selection:default", "unexpected")
            .commit()
        val config = DemoWalletConfig(signingProtectionMode = WalletDemoSigningProtectionMode.Optional)

        assertThrows(IllegalStateException::class.java) {
            config.selectedSigningProtection(context)
        }
    }

    @Test
    fun managedModesIgnoreASelectionTheyDoNotAllow() {
        val required = DemoWalletConfig(signingProtectionMode = WalletDemoSigningProtectionMode.Required)
        required.signingProtectionStore(context).save(WalletDemoSigningProtection.None)

        assertEquals(WalletDemoSigningProtection.Biometric, required.selectedSigningProtection(context))

        val disabled = required.copy(signingProtectionMode = WalletDemoSigningProtectionMode.Disabled)
        assertEquals(WalletDemoSigningProtection.None, disabled.selectedSigningProtection(context))
    }
}
