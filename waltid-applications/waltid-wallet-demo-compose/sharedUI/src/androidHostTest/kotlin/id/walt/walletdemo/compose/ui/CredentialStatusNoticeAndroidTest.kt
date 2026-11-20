package id.walt.walletdemo.compose.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import id.walt.walletdemo.compose.logic.WalletDemoCredentialStatusNotice
import id.walt.walletdemo.compose.logic.WalletSessionState
import id.walt.walletdemo.compose.ui.screens.CredentialsTab
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
        val notices = mutableStateOf(listOf(
            WalletDemoCredentialStatusNotice(first.id, "first-notice", "REVOKED", "2026-11-20T12:00:00Z", false),
            WalletDemoCredentialStatusNotice(second.id, "second-notice", "SUSPENDED", "2026-11-20T12:00:00Z", true),
        ))
        val acknowledged = mutableListOf<String>()
        var closeDetails = {}
        setContent {
            MaterialTheme {
                CredentialsTab(
                    session = wallet.copy(credentials = listOf(first, second)),
                    statusNotices = notices.value,
                    onDetailsChromeChange = { chrome -> closeDetails = chrome?.onClose ?: {} },
                    onAcknowledgeStatusNotice = { notice ->
                        acknowledged += notice.version
                        notices.value = notices.value.filterNot { it.version == notice.version }
                    },
                )
            }
        }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("View credential").fetchSemanticsNodes().size == 1 }
        onNodeWithText("View credential").performClick()
        onNodeWithTag(WalletUiTestTags.CredentialDetailsScreen).assertIsDisplayed()
        runOnIdle {
            assertEquals(listOf("first-notice"), acknowledged)
            closeDetails()
        }
        onNodeWithText("Last reported Suspended", substring = true).assertIsDisplayed()
    }
}
