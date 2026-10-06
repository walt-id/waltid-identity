package id.walt.walletdemo.compose.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.StateRestorationTester
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import id.walt.walletdemo.compose.logic.WalletDemoIdentitySetup
import id.walt.walletdemo.compose.logic.WalletDemoKeyChoice
import id.walt.walletdemo.compose.logic.WalletDemoKeySetupOption
import id.walt.walletdemo.compose.logic.WalletDemoIdentityDetails
import id.walt.walletdemo.compose.logic.WalletDemoIdentityDetailsState
import id.walt.walletdemo.compose.logic.WalletDemoUiState
import id.walt.walletdemo.compose.ui.screens.SettingsScreen
import id.walt.walletdemo.compose.ui.screens.IdentitySetupScreen
import kotlin.test.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KeySetupNavigationAndroidTest {
    @Test
    fun settingsDoNotOfferLegacySigningControlsWhileDetailsLoadOrFail() = runAndroidComposeUiTest<ComponentActivity> {
        val state = mutableStateOf(WalletDemoUiState())
        setContent {
            SettingsScreen(
                state = state.value,
                onShowDcApiPresentationPreviewChange = {},
                onProximityTransportProfileChange = null,
                onBack = {},
                onIdentityAction = {},
                onRefreshIdentityDetails = {},
                onLock = {},
                onResetWallet = {},
                onRequestSigningProtectionChange = {},
                onConfirmSigningProtectionChange = {},
                onCancelSigningProtectionChange = {},
            )
        }
        onNodeWithText("Signing key").performClick()
        onAllNodesWithText("Signing protection").assertCountEquals(0)
        runOnUiThread {
            state.value = state.value.copy(identityDetails = WalletDemoIdentityDetailsState.Failed("Provider unavailable"))
        }
        onNodeWithText("Provider unavailable").performScrollTo().assertIsDisplayed()
        onAllNodesWithText("Signing protection").assertCountEquals(0)
        runOnUiThread {
            state.value = state.value.copy(identityDetails = WalletDemoIdentityDetailsState.Available(
                WalletDemoIdentityDetails("Hardware", "Generated", "No signing prompt", "No backup", emptyList())))
        }
        onNodeWithText("Key storage").performScrollTo().assertIsDisplayed()
        onNodeWithText("Unknown").performScrollTo().assertIsDisplayed()
        onAllNodesWithText("Changing signing protection creates a new wallet key and DID.").assertCountEquals(0)
        runOnUiThread {
            state.value = state.value.copy(identityDetails = WalletDemoIdentityDetailsState.Unsupported)
        }
        onNodeWithText("Signing protection").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun pinFreeSettingsKeepAccountActionsAndHideDeviceControls() = runAndroidComposeUiTest<ComponentActivity> {
        var signedOut = false
        var reset = false
        setContent {
            SettingsScreen(
                state = WalletDemoUiState(pinLockEnabled = false, identityDetails = WalletDemoIdentityDetailsState.Unsupported),
                onShowDcApiPresentationPreviewChange = {},
                onProximityTransportProfileChange = null,
                onBack = {},
                onIdentityAction = {},
                onRefreshIdentityDetails = {},
                onLock = { error("PIN-free wallets must not expose Lock") },
                onResetWallet = { reset = true },
                onRequestSigningProtectionChange = {},
                onConfirmSigningProtectionChange = {},
                onCancelSigningProtectionChange = {},
                onSignOut = { signedOut = true },
                resetWalletDescription = "Delete the current server wallet and create an empty wallet.",
            )
        }
        for (tag in listOf(WalletUiTestTags.SettingsLock, WalletUiTestTags.SettingsSigningKey,
            WalletUiTestTags.SettingsCredentialSharing, WalletUiTestTags.SettingsDigitalCredentialsApi)) {
            onAllNodesWithTag(tag).assertCountEquals(0)
        }
        onNodeWithTag(WalletUiTestTags.SettingsTechnicalDetails).performClick()
        onNodeWithTag(WalletUiTestTags.SettingsBack).performClick()
        onNodeWithTag(WalletUiTestTags.SettingsSignOut).performScrollTo().performClick()
        kotlin.test.assertTrue(signedOut)
        onNodeWithTag(WalletUiTestTags.SettingsReset).performScrollTo().performClick()
        kotlin.test.assertFalse(reset)
        onNodeWithText("Delete the current server wallet and create an empty wallet.").assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.SettingsResetConfirm).performClick()
        kotlin.test.assertTrue(reset)
    }

    @Test
    fun settingsRestoresNestedDestinationAndShowsLivePreferenceAndOperationErrors() = runAndroidComposeUiTest<ComponentActivity> {
        val state = mutableStateOf(WalletDemoUiState(identityDetails = WalletDemoIdentityDetailsState.Available(
            WalletDemoIdentityDetails("Native", "Generated", "None", "No backup", emptyList()))))
        var exited = false
        val restoration = StateRestorationTester(this)
        restoration.setContent {
            SettingsScreen(state.value, { state.value = state.value.copy(showDcApiPresentationPreview = it) },
                { state.value = state.value.copy(proximityTransportProfile = it) }, { exited = true }, {}, {}, {}, {}, {}, {}, {},
                readerTrustSettingsContent = { androidx.compose.material3.Text("Reader trust editor") })
        }
        onAllNodesWithText("Wallet actions").assertCountEquals(0)
        onAllNodesWithTag(WalletUiTestTags.SettingsReaderAuthentication).assertCountEquals(0)
        onNodeWithTag(WalletUiTestTags.SettingsProximityPresentation).performScrollTo().performClick()
        onNodeWithTag(WalletUiTestTags.SettingsReaderAuthentication).performScrollTo().performClick()
        restoration.emulateSaveAndRestore()
        onNodeWithText("Reader trust editor").assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.SettingsBack).performClick()
        onNodeWithText("Connection method").performScrollTo().assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.SettingsBack).performClick()
        onNodeWithText("Digital Credentials API").performScrollTo().performClick()
        onNodeWithTag(WalletUiTestTags.SettingsShowDcApiPreview).assertIsOn().performClick().assertIsOff()
        restoration.emulateSaveAndRestore()
        onNodeWithTag(WalletUiTestTags.SettingsShowDcApiPreview).assertIsOff()
        onNodeWithTag(WalletUiTestTags.SettingsBack).performClick()
        onNodeWithText("Signing key").performScrollTo().performClick()
        runOnUiThread { state.value = state.value.copy(identityError = "Backup provider unavailable") }
        onNodeWithText("Backup provider unavailable").performScrollTo().assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.SettingsBack).performClick()
        onNodeWithTag(WalletUiTestTags.SettingsLock).performScrollTo().assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.SettingsReset).performScrollTo().assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.SettingsBack).performClick()
        kotlin.test.assertTrue(exited)
    }

    @Test
    fun pendingConflictExplainsCancellationWithoutOfferingBlindRetry() = runAndroidComposeUiTest<ComponentActivity> {
        var cancelled = false
        setContent {
            IdentitySetupScreen(WalletDemoIdentitySetup.Pending("pending", "A different backup uses this identifier.", false),
                null, {}, {}, { cancelled = it == "pending" }, {})
        }
        onNodeWithText("A different backup uses this identifier.").assertIsDisplayed()
        onAllNodesWithText("Retry setup").assertCountEquals(0)
        onNodeWithText("Cancel pending setup").performClick()
        kotlin.test.assertTrue(cancelled)
    }

    @Test
    fun activeKeyShowsProviderFailuresWithRefreshInProtectionSettings() = runAndroidComposeUiTest<ComponentActivity> {
        var refreshed = false
        val state = WalletDemoUiState(identityDetails = WalletDemoIdentityDetailsState.Available(
            WalletDemoIdentityDetails("Android Keystore", "Imported", "No signing prompt", "Saved locally", emptyList(),
                protection = "StrongBox", providerFailures = listOf("Backup provider requires sign-in."))))
        setContent { SettingsScreen(
                state = state,
                onShowDcApiPresentationPreviewChange = {},
                onProximityTransportProfileChange = null,
                onBack = {},
                onIdentityAction = {},
                onRefreshIdentityDetails = { refreshed = true },
                onLock = {},
                onResetWallet = {},
                onRequestSigningProtectionChange = {},
                onConfirmSigningProtectionChange = {},
                onCancelSigningProtectionChange = {},
            ) }
        onNodeWithText("Signing key").performClick()
        onNodeWithText("StrongBox").performScrollTo().assertIsDisplayed()
        onNodeWithText("Backup provider requires sign-in.").performScrollTo().assertIsDisplayed()
        onNodeWithText("Check again").performScrollTo().performClick()
        kotlin.test.assertTrue(refreshed)
        onNodeWithTag(WalletUiTestTags.SettingsBack).performClick()
        onNodeWithText("Technical details").assertIsDisplayed()
    }

    @Test
    fun emptyOptionsStillShowProviderFailureAndAllowRefresh() = runAndroidComposeUiTest<ComponentActivity> {
        var refreshed = false
        setContent {
            IdentitySetupScreen(WalletDemoIdentitySetup.Choose(emptyList(), recoveryUnavailableReasons = listOf("Sign in to your backup provider.")),
                null, {}, {}, {}, { refreshed = true })
        }
        onNodeWithText("Sign in to your backup provider.").assertIsDisplayed()
        onAllNodesWithText("Check again").assertCountEquals(0)
        onNodeWithText("Try again").performClick()
        kotlin.test.assertTrue(refreshed)
        onAllNodesWithText("Continue").assertCountEquals(0)
    }

    @Test
    fun unavailableCloudShowsReasonAndRetryWithoutHidingTransfer() = runAndroidComposeUiTest<ComponentActivity> {
        fun choice(id: String) = WalletDemoKeyChoice(id, id, "Details for $id")
        val option = WalletDemoKeySetupOption("transfer", choice("Prepare device transfer"), choice("native"), choice("none"))
        val setup = mutableStateOf(WalletDemoIdentitySetup.Choose(listOf(option),
            recoveryUnavailableReasons = listOf("Encrypted cloud backup: Google reports encryption unavailable.")))
        var refreshed = false
        val warning = mutableStateOf<String?>("Key options unavailable")
        setContent { IdentitySetupScreen(setup.value, warning.value, {}, {}, {}, {
            refreshed = true
            warning.value = null
            setup.value = setup.value.copy(recoveryUnavailableReasons = emptyList())
        }) }
        onAllNodesWithText("Try again").assertCountEquals(1)
        onAllNodesWithText("Check again").assertCountEquals(0)
        onNodeWithTag("wallet.keySetupEdit.Recovery").performClick()
        onAllNodesWithText("Key options unavailable").assertCountEquals(0)
        onAllNodesWithText("Try again").assertCountEquals(0)
        onNodeWithText("Encrypted cloud backup: Google reports encryption unavailable.").performScrollTo().assertIsDisplayed()
        onNodeWithTag(WalletUiTestTags.keySetupChoice("Recovery", 0)).assertIsDisplayed()
        onNodeWithText("Check again").performScrollTo().performClick()
        kotlin.test.assertTrue(refreshed)
        onAllNodesWithText("Try again").assertCountEquals(0)
        onNodeWithTag(WalletUiTestTags.keySetupChoice("Recovery", 0)).assertIsDisplayed()
        onNodeWithText("Done").performClick()
        onNodeWithText("Create signing key").assertIsDisplayed()
    }

    @Test
    fun systemBackAndRefreshedHandlesKeepTheSelectedConfiguration() = runAndroidComposeUiTest<ComponentActivity> {
        fun choice(id: String) = WalletDemoKeyChoice(id, id, "Details for $id")
        val options = listOf("native", "database").map { storage ->
            WalletDemoKeySetupOption(storage, choice("backup"), choice(storage), choice("none"))
        }
        val setup = mutableStateOf(WalletDemoIdentitySetup.Choose(options))
        var submitted: String? = null
        val restoration = StateRestorationTester(this)
        restoration.setContent { IdentitySetupScreen(setup.value, null, { submitted = it }, {}, {}, {}) }
        onNodeWithTag("wallet.keySetupEdit.Storage").performClick()
        onNodeWithTag(WalletUiTestTags.keySetupChoice("Storage", 1)).performScrollTo().performClick()
        kotlin.test.assertNull(submitted)
        runOnUiThread { setup.value = WalletDemoIdentitySetup.Choose(options.map { it.copy(id = "refreshed-${it.id}") }) }
        restoration.emulateSaveAndRestore()
        onNodeWithTag(WalletUiTestTags.keySetupChoice("Storage", 1)).assertIsSelected()
        runOnUiThread { requireNotNull(activity).onBackPressedDispatcher.onBackPressed() }
        onNodeWithTag("wallet.keySetupEdit.Storage").assertTextContains("database")
        kotlin.test.assertNull(submitted)
        restoration.emulateSaveAndRestore()
        onNodeWithTag(WalletUiTestTags.KeySetupContinue).performClick()
        kotlin.test.assertEquals("refreshed-database", submitted)
        onNodeWithTag("wallet.keySetupEdit.Storage").performClick()
        runOnUiThread { setup.value = WalletDemoIdentitySetup.Choose(options.take(1)) }
        onNodeWithTag("wallet.keySetupEdit.Storage").assertTextContains("native")
        onAllNodesWithTag(WalletUiTestTags.keySetupChoice("Storage", 1)).assertCountEquals(0)
        runOnUiThread { setup.value = WalletDemoIdentitySetup.Choose(options) }
        onNodeWithTag("wallet.keySetupEdit.Storage").performClick()
        onNodeWithTag(WalletUiTestTags.keySetupChoice("Storage", 0)).assertIsSelected()
    }
}
