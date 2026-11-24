package id.walt.walletdemo.compose.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import id.walt.walletdemo.compose.logic.DemoWallet
import id.walt.walletdemo.compose.logic.InMemoryDemoPinStore
import id.walt.walletdemo.compose.logic.InMemoryDemoSharingSettingsStore
import id.walt.walletdemo.compose.logic.WalletDemoController
import id.walt.walletdemo.compose.logic.WalletDemoCredentialStatusNotice
import id.walt.walletdemo.compose.logic.WalletDemoCredentialStatuses
import id.walt.walletdemo.compose.logic.WalletSessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CredentialStatusNoticeAndroidTest {
    @Test
    fun onlyTheActionOpensDetailsAndSwipeAndCloseDismissTheNotice() = runAndroidComposeUiTest<ComponentActivity> {
        val wallet = WalletVisualFixtures.partialResult.session as WalletSessionState.Ready
        val first = wallet.credentials.first()
        val second = first.copy(id = "other-credential")
        val third = first.copy(id = "third-credential")
        val notices = listOf(
            WalletDemoCredentialStatusNotice(first.id, "first-notice", "REVOKED", "2026-11-20T12:00:00Z", false),
            WalletDemoCredentialStatusNotice(second.id, "second-notice", "SUSPENDED", "2026-11-20T12:00:00Z", true),
            WalletDemoCredentialStatusNotice(third.id, "third-notice", "REVOKED", "2026-11-20T12:00:00Z", false),
        )
        val snapshots = MutableStateFlow(WalletDemoCredentialStatuses(pendingNotices = emptyList()))
        val demoWallet = object : DemoWallet by WalletUiTestWallet(listOf(first, second, third)) {
            override fun credentialStatusSnapshots() = snapshots
            override suspend fun acknowledgeCredentialStatusNotice(credentialId: String, version: String) {
                snapshots.update { snapshot ->
                    snapshot.copy(pendingNotices = snapshot.pendingNotices.orEmpty().filterNot {
                        it.credentialId == credentialId && it.version == version
                    })
                }
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val controller = WalletDemoController(demoWallet, InMemoryDemoPinStore(), skipPin = true, scope = scope,
            sharingSettings = InMemoryDemoSharingSettingsStore().apply { setAutomaticCredentialStatusChecks(false) })
        try {
            setContent { WalletDemoApp(controller) }
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(WalletUiTestTags.SettingsButton).fetchSemanticsNodes().size == 1 }
            onNodeWithTag(WalletUiTestTags.SettingsButton).performClick()
            onNodeWithTag(WalletUiTestTags.SettingsBack).assertIsDisplayed()
            snapshots.update { it.copy(pendingNotices = notices.take(1)) }
            waitUntil(timeoutMillis = 5_000) { controller.state.value.credentialStatuses.pendingNotices == notices.take(1) }
            onAllNodesWithText("View credential").assertCountEquals(0)

            onNodeWithTag(WalletUiTestTags.SettingsBack).performClick()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("View credential").fetchSemanticsNodes().size == 1 }
            onAllNodesWithText("2 more").assertCountEquals(0)
            snapshots.update { it.copy(pendingNotices = notices) }
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("2 more").fetchSemanticsNodes().size == 1 }
            onAllNodesWithTag(WalletUiTestTags.CredentialStatusNotice).assertCountEquals(1)
            onNodeWithText("Resident card - Revoked").performTouchInput { click() }
            onAllNodesWithTag(WalletUiTestTags.CredentialDetailsScreen).assertCountEquals(0)
            assertEquals(notices, snapshots.value.pendingNotices)
            onNodeWithText("View credential", useUnmergedTree = true).performTouchInput { click() }
            onNodeWithTag(WalletUiTestTags.CredentialDetailsScreen).assertIsDisplayed()
            waitUntil(timeoutMillis = 5_000) { snapshots.value.pendingNotices == notices.drop(1) }
            onAllNodesWithText("View credential").assertCountEquals(0)

            onNodeWithTag(WalletUiTestTags.DetailsBack).performClick()
            onNodeWithText("(last known)", substring = true).assertIsDisplayed()
            onNodeWithText("1 more").assertIsDisplayed()
            onNodeWithTag(WalletUiTestTags.CredentialStatusNotice).performTouchInput { swipeLeft() }
            waitUntil(timeoutMillis = 5_000) { snapshots.value.pendingNotices == notices.drop(2) }
            onAllNodesWithTag(WalletUiTestTags.CredentialDetailsScreen).assertCountEquals(0)
            onAllNodesWithText("1 more").assertCountEquals(0)

            onNodeWithContentDescription("Dismiss credential status notice").performTouchInput { click() }
            waitUntil(timeoutMillis = 5_000) { snapshots.value.pendingNotices.orEmpty().isEmpty() }
            onAllNodesWithTag(WalletUiTestTags.CredentialStatusNotice).assertCountEquals(0)
            onAllNodesWithTag(WalletUiTestTags.CredentialDetailsScreen).assertCountEquals(0)
        } finally {
            scope.cancel()
        }
    }
}
