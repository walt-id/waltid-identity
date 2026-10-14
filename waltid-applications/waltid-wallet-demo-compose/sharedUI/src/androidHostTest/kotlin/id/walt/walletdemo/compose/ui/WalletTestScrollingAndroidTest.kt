package id.walt.walletdemo.compose.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CompletableDeferred
import kotlin.test.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WalletTestScrollingAndroidTest {
    @Test
    fun scrollWaitsForAsynchronousContentBeforeMakingItVisible() = runComposeUiTest {
        val publishContent = CompletableDeferred<Unit>()
        val ready = mutableStateOf(false)
        setContent {
            LaunchedEffect(Unit) {
                publishContent.await()
                ready.value = true
            }
            Column(Modifier.height(160.dp).verticalScroll(rememberScrollState())) {
                Text("Loading")
                Spacer(Modifier.height(480.dp))
                if (ready.value) Text("Ready", Modifier.testTag("ready"))
            }
        }
        onAllNodesWithTag("ready").assertCountEquals(0)

        // Publish only after the first lookup, so the original semantics tree has no target.
        onNode(SemanticsMatcher("asynchronously published content") { node ->
            publishContent.complete(Unit)
            node.config.getOrNull(SemanticsProperties.TestTag) == "ready"
        }).performScrollToContent(this).assertIsDisplayed()
    }
}
