package id.walt.walletdemo.compose.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ViewRootForTest
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

/** Exercises the production modal flow and its own window, outside the home Scaffold. */
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
            val navigationInset = 48
            runOnUiThread { WindowCompat.setDecorFitsSystemWindows(requireNotNull(activity).window, false) }
            try {
                setContent {
                    WalletDemoTheme {
                        WalletScreen(controller, state)
                    }
                }
                val submit = onNodeWithTag(WalletUiTestTags.PresentationSubmitButton)
                val beforeInsets = submit.assertIsDisplayed().fetchSemanticsNode()
                val bottomBeforeInsets = beforeInsets.boundsInRoot.bottom
                // ModalBottomSheet owns a separate window; dispatch there rather than to the activity.
                val sheetView = (requireNotNull(beforeInsets.root) as ViewRootForTest).view
                runOnUiThread {
                    ViewCompat.dispatchApplyWindowInsets(sheetView.rootView,
                        WindowInsetsCompat.Builder().setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, navigationInset))
                            .setVisible(WindowInsetsCompat.Type.navigationBars(), true).build())
                }
                waitForIdle()
                assertEquals(navigationInset.toFloat(), bottomBeforeInsets - submit.fetchSemanticsNode().boundsInRoot.bottom,
                    "The sheet must apply the simulated three-button navigation inset exactly once")
                onNodeWithText("MEM-1234").performScrollToContent(this).assertIsDisplayed()
                val sheet = onNodeWithTag("wallet.review.sheet").fetchSemanticsNode().boundsInRoot
                val action = submit.assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                val lastValue = onNodeWithText("MEM-1234").fetchSemanticsNode().boundsInRoot
                assertTrue(action.bottom <= sheet.bottom - navigationInset, "Sharing actions must stay outside the navigation bar")
                assertTrue(lastValue.bottom <= action.top, "The last information row must scroll above the pinned actions")
            } finally { scope.cancel() }
        }
}
