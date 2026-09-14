package id.walt.walletdemo.compose.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import id.walt.walletdemo.compose.logic.ClaimItem
import id.walt.walletdemo.compose.logic.ClaimItemPath
import id.walt.walletdemo.compose.logic.DisplayValue
import id.walt.walletdemo.compose.ui.components.ClaimValueRow
import kotlin.test.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ClaimValueRowAndroidTest {
    @Test
    fun largeListRendersABoundedPreview() = runComposeUiTest {
        setContent {
            WalletDemoTheme {
                ClaimValueRow(
                    item = ClaimItem(
                        path = ClaimItemPath.topLevel("unknown_binary"),
                        label = "Unknown binary",
                        value = DisplayValue.ListValue(
                            values = List(30) { index -> DisplayValue.NumberValue("item $index") },
                        ),
                    ),
                )
            }
        }

        onNodeWithText("item 24").assertExists()
        onNodeWithText("item 25").assertDoesNotExist()
        onNodeWithText("Showing first 25 of 30 items").assertExists()
    }
}
