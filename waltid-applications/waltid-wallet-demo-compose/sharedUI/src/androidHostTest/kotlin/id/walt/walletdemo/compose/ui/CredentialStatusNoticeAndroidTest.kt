package id.walt.walletdemo.compose.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
    fun theExistingSnackbarOpensDetailsAndLeavesTheNextNoticeForHome() = runAndroidComposeUiTest<ComponentActivity> {
        val wallet = WalletVisualFixtures.partialResult.session as WalletSessionState.Ready
        val first = wallet.credentials.first()
        val second = first.copy(id = "other-credential")
        val notices = listOf(
            WalletDemoCredentialStatusNotice(first.id, "first-notice", "REVOKED", "2026-11-20T12:00:00Z", false),
            WalletDemoCredentialStatusNotice(second.id, "second-notice", "SUSPENDED", "2026-11-20T12:00:00Z", true),
        )
        val snapshots = MutableStateFlow(WalletDemoCredentialStatuses(pendingNotices = emptyList()))
        val demoWallet = object : DemoWallet by WalletUiTestWallet(listOf(first, second)) {
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
            snapshots.update { it.copy(pendingNotices = notices) }
            waitUntil(timeoutMillis = 5_000) { controller.state.value.credentialStatuses.pendingNotices == notices }
            onAllNodesWithText("View credential").assertCountEquals(0)

            onNodeWithTag(WalletUiTestTags.SettingsBack).performClick()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("View credential").fetchSemanticsNodes().size == 1 }
            onNodeWithText("View credential").performClick()
            onNodeWithTag(WalletUiTestTags.CredentialDetailsScreen).assertIsDisplayed()
            waitUntil(timeoutMillis = 5_000) { snapshots.value.pendingNotices == notices.drop(1) }
            onAllNodesWithText("View credential").assertCountEquals(0)

            onNodeWithTag(WalletUiTestTags.DetailsBack).performClick()
            onNodeWithText("Last reported Suspended", substring = true).assertIsDisplayed()
            assertEquals(notices.drop(1), snapshots.value.pendingNotices)
        } finally {
            scope.cancel()
        }
    }
}
