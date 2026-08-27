package id.walt.walletdemo.compose.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import id.walt.walletdemo.compose.ui.components.CredentialCardArt
import id.walt.walletdemo.compose.ui.components.CredentialCardArtModel
import kotlin.test.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CredentialCardLogoAndroidTest {
    @Test
    fun constructedCardShowsBundledWaltLogo() = runComposeUiTest {
        setContent {
            WalletDemoTheme {
                CredentialCardArt(
                    art = CredentialCardArtModel(
                        id = "cred-1",
                        name = "Personal ID",
                    ),
                )
            }
        }
        onNodeWithTag(WalletUiTestTags.CredentialCardConstructedArt).assertIsDisplayed()
        onNodeWithContentDescription("walt.id").assertIsDisplayed()
    }
}
