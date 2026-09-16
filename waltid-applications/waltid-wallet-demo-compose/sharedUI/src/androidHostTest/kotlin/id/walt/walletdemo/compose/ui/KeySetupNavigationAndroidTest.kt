package id.walt.walletdemo.compose.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
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
            SettingsScreen(state.value, {}, {}, {}, {}, {}, {}, {}, {}, {})
        }
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
        onNodeWithText("Wallet signing key").performScrollTo().assertIsDisplayed()
        onAllNodesWithText("Signing protection").assertCountEquals(0)
        runOnUiThread {
            state.value = state.value.copy(identityDetails = WalletDemoIdentityDetailsState.Unsupported)
        }
        onNodeWithText("Signing protection").performScrollTo().assertIsDisplayed()
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
        onNodeWithText("Encrypted cloud backup: Google reports encryption unavailable.").performScrollTo().assertIsDisplayed()
        onNodeWithText("Prepare device transfer").performScrollTo().assertIsDisplayed()
        onNodeWithText("Try again").performScrollTo().performClick()
        kotlin.test.assertTrue(refreshed)
        onAllNodesWithText("Try again").assertCountEquals(0)
        onNodeWithText("Prepare device transfer").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun systemBackAndRefreshedHandlesKeepTheSelectedConfiguration() = runAndroidComposeUiTest<ComponentActivity> {
        fun choice(id: String) = WalletDemoKeyChoice(id, id, "Details for $id")
        val options = listOf("native", "database").map { storage ->
            WalletDemoKeySetupOption(storage, choice("backup"), choice(storage), choice("none"))
        }
        val setup = mutableStateOf(WalletDemoIdentitySetup.Choose(options))
        setContent { IdentitySetupScreen(setup.value, null, {}, {}, {}, {}) }
        onNodeWithTag(WalletUiTestTags.KeySetupContinue).performClick()
        onNodeWithTag(WalletUiTestTags.keySetupChoice("Storage", 1)).performScrollTo().performClick()
        onNodeWithTag(WalletUiTestTags.KeySetupContinue).performClick()
        onAllNodesWithText("Back").assertCountEquals(0)

        runOnUiThread { setup.value = WalletDemoIdentitySetup.Choose(options.map { it.copy(id = "refreshed-${it.id}") }) }
        onNodeWithText("3 of 3 · Signing approval").assertIsDisplayed()
        runOnUiThread { requireNotNull(activity).onBackPressedDispatcher.onBackPressed() }
        onNodeWithTag(WalletUiTestTags.keySetupChoice("Storage", 1)).assertIsSelected()
        runOnUiThread { requireNotNull(activity).onBackPressedDispatcher.onBackPressed() }
        onNodeWithText("1 of 3 · Recovery").assertIsDisplayed()
        onAllNodesWithText("Refresh available options").assertCountEquals(0)

        onNodeWithTag(WalletUiTestTags.KeySetupContinue).performClick()
        onNodeWithTag(WalletUiTestTags.KeySetupContinue).performClick()
        runOnUiThread { setup.value = WalletDemoIdentitySetup.Choose(options.take(1)) }
        onNodeWithText("1 of 3 · Recovery").assertIsDisplayed()
        runOnUiThread { setup.value = WalletDemoIdentitySetup.Choose(options) }
        onNodeWithTag(WalletUiTestTags.KeySetupContinue).performClick()
        onNodeWithTag(WalletUiTestTags.keySetupChoice("Storage", 0)).assertIsSelected()
    }
}
