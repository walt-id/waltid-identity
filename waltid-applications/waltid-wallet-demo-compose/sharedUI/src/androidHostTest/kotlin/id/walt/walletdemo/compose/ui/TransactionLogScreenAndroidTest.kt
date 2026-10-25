package id.walt.walletdemo.compose.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import id.walt.walletdemo.compose.logic.WalletDemoTransactionLogClaim
import id.walt.walletdemo.compose.logic.WalletDemoTransactionLogCredential
import id.walt.walletdemo.compose.logic.WalletDemoTransactionLogEntry
import id.walt.walletdemo.compose.logic.WalletDemoTransactionLogResult
import id.walt.walletdemo.compose.logic.WalletDemoTransactionLogType
import id.walt.walletdemo.compose.ui.screens.TransactionLogEntryCard
import kotlin.test.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TransactionLogScreenAndroidTest {
    @Test
    fun expandedPresentationShowsRequestedAndPresentedClaimsForEachCredential() = runComposeUiTest {
        val entry = entry.copy(
            credentials = listOf(
                WalletDemoTransactionLogCredential(
                    "Identity",
                    listOf(WalletDemoTransactionLogClaim("given_name"), WalletDemoTransactionLogClaim("family_name")),
                    listOf(WalletDemoTransactionLogClaim("given_name")),
                ),
                WalletDemoTransactionLogCredential(
                    "DrivingLicence",
                    listOf(WalletDemoTransactionLogClaim("""["org.iso.18013.5.1","birth_date"]""")),
                    emptyList(),
                ),
            ),
        )
        setContent {
            WalletDemoTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    TransactionLogEntryCard(entry, expanded = true, onToggle = {}, onDelete = {})
                }
            }
        }

        onAllNodesWithTag("transactionLog.credential", useUnmergedTree = true).assertCountEquals(2)
        onNodeWithText("Identity", useUnmergedTree = true).assertExists()
        onNodeWithText("DrivingLicence", useUnmergedTree = true).assertExists()
        onAllNodesWithText("Claims requested", useUnmergedTree = true).assertCountEquals(2)
        onAllNodesWithText("Claims presented", useUnmergedTree = true).assertCountEquals(2)
        onAllNodesWithText("Given name", useUnmergedTree = true).assertCountEquals(2)
        onAllNodesWithText("given_name", useUnmergedTree = true).assertCountEquals(2)
        onAllNodesWithText("Family name", useUnmergedTree = true).assertCountEquals(1)
        onNodeWithText("Date of birth", useUnmergedTree = true).assertExists()
        onNodeWithText("""["org.iso.18013.5.1","birth_date"]""", useUnmergedTree = true).assertExists()
        onNodeWithText("None", useUnmergedTree = true).assertExists()
    }

    @Test
    fun emptyPresentationExplainsThatNoClaimsWereRecorded() = runComposeUiTest {
        setContent {
            WalletDemoTheme {
                TransactionLogEntryCard(entry, expanded = true, onToggle = {}, onDelete = {})
            }
        }

        onNodeWithText("No requested or presented claims recorded.", useUnmergedTree = true).assertExists()
        onAllNodesWithTag("transactionLog.credential", useUnmergedTree = true).assertCountEquals(0)
    }

    private val entry = WalletDemoTransactionLogEntry(
        transactionIdentifier = "presentation",
        type = WalletDemoTransactionLogType.Presentation,
        time = "2026-10-25T16:00",
        result = WalletDemoTransactionLogResult.NotCompleted,
    )
}
