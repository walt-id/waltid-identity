package id.walt.walletdemo.compose.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import id.walt.walletdemo.compose.logic.*
import id.walt.walletdemo.compose.ui.screens.WalletScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Exercises the production in-app flow host, which bypasses the home Scaffold. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], qualifiers = "en-rUS-w393dp-h852dp-mdpi")
class WalletFlowInsetsAndroidTest {
    @Test fun sharingActionsAndFinalInformationRemainAboveNavigationBar() =
        runAndroidComposeUiTest<ComponentActivity> {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val controller = WalletDemoController(WalletUiTestWallet(), InMemoryDemoPinStore(), scope = scope)
            val options = WalletVisualFixtures.informationCredentials
            val preview = WalletDemoPresentationPreview(WalletDemoPresentationPreviewHandle("insets"), null, null,
                responseEncryption = WalletDemoResponseEncryption.NotRequired, credentialOptions = options,
                credentialRequirements = listOf(WalletDemoPresentationCredentialRequirement(listOf(listOf("identity", "membership")))))
            val state = WalletVisualFixtures.partialResult.copy(selectedTab = WalletDemoTab.Present,
                operation = WalletOperationState.Idle, presentationReview = WalletDemoPresentationPreviewResult.Ready(preview),
                selectedPresentationCredentialOptions = preview.defaultCredentialSelection())
            var observedNavigationInset = 0
            runOnUiThread { WindowCompat.setDecorFitsSystemWindows(requireNotNull(activity).window, false) }
            try {
                setContent {
                    WalletDemoTheme {
                        val density = LocalDensity.current
                        val inset = WindowInsets.navigationBars.getBottom(density)
                        SideEffect { observedNavigationInset = inset }
                        Box(Modifier.fillMaxSize().testTag("flow-host")) { WalletScreen(controller, state) }
                    }
                }
                runOnUiThread {
                    ViewCompat.dispatchApplyWindowInsets(requireNotNull(activity).window.decorView,
                        WindowInsetsCompat.Builder().setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 48))
                            .setVisible(WindowInsetsCompat.Type.navigationBars(), true).build())
                }
                waitForIdle()
                assertEquals(48, observedNavigationInset, "The host must receive the simulated three-button navigation inset")
                onNodeWithText("MEM-1234").performScrollTo().assertIsDisplayed()
                val host = onNodeWithTag("flow-host").fetchSemanticsNode().boundsInRoot
                val action = onNodeWithTag(WalletUiTestTags.PresentationSubmitButton).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                val lastValue = onNodeWithText("MEM-1234").fetchSemanticsNode().boundsInRoot
                assertTrue(action.bottom <= host.bottom - 48, "Sharing actions must stay outside the navigation bar")
                assertTrue(lastValue.bottom <= action.top, "The last information row must scroll above the pinned actions")
            } finally { scope.cancel() }
        }
}
