package id.walt.walletdemo.compose.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.ClaimGroup
import id.walt.walletdemo.compose.logic.ClaimItem
import id.walt.walletdemo.compose.logic.ClaimItemPath
import id.walt.walletdemo.compose.logic.CredentialDisplayNormalizer
import id.walt.walletdemo.compose.logic.CredentialSummary
import id.walt.walletdemo.compose.logic.DisplayValue
import id.walt.walletdemo.compose.ui.components.ClaimGroupSection
import id.walt.walletdemo.compose.ui.components.ClaimValueRow
import kotlin.test.Test
import kotlin.test.assertIs
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ClaimValueRowAndroidTest {
    @Test
    fun offscreenImageWaitsUntilItsRowIsVisible() = runComposeUiTest {
        val item = deferredInvalidImage()
        setContent {
            WalletDemoTheme {
                Column(Modifier.height(300.dp).verticalScroll(rememberScrollState())) {
                    Spacer(Modifier.height(1200.dp))
                    ClaimValueRow(item)
                }
            }
        }
        waitForIdle()
        onNodeWithText(unavailable).assertDoesNotExist()
        onNodeWithTag(WalletUiTestTags.claim("visual_proof")).performScrollTo()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(unavailable).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun collapsedImageWaitsUntilItsGroupIsExpanded() = runComposeUiTest {
        val item = deferredInvalidImage()
        setContent {
            WalletDemoTheme {
                ClaimGroupSection(ClaimGroup("Images", listOf(item), initiallyExpanded = false))
            }
        }
        waitForIdle()
        onNodeWithText(unavailable).assertDoesNotExist()
        onNodeWithText("1 entry").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(unavailable).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun deferredInvalidImage(): ClaimItem {
        val details = CredentialDisplayNormalizer.toDetails(CredentialSummary(
            id = "image", format = "vc+sd-jwt", issuer = null, label = "Image",
            credentialDataJson = """{"visual_proof":"data:image/png;base64,iVBORw0KGgo="}""",
        ))
        return details.groups.single().items.single().also { assertIs<DisplayValue.DeferredImage>(it.value) }
    }

    private val unavailable = "Image unavailable or unsupported"

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
