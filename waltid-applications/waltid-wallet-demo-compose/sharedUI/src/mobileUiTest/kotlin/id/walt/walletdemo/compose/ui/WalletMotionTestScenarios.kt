package id.walt.walletdemo.compose.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletSessionState
import id.walt.walletdemo.compose.logic.toCardDisplayData
import id.walt.walletdemo.compose.ui.components.CredentialCardStack
import id.walt.walletdemo.compose.ui.screens.CredentialDetailsChrome
import id.walt.walletdemo.compose.ui.screens.CredentialsTab
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler

@OptIn(ExperimentalTestApi::class)
class WalletMotionTestScenarios(
    private val platformTheme: @Composable (@Composable () -> Unit) -> Unit = { it() },
) {
    @Composable private fun theme(content: @Composable () -> Unit) = platformTheme { WalletDemoTheme(content = content) }

    fun closingAndReopeningDetailsDuringMotionDoesNotReplayAStaleTransition() = motionTest { effects ->
        val session = WalletVisualFixtures.partialResult.session as WalletSessionState.Ready
        val first = session.credentials.single()
        var chrome: CredentialDetailsChrome? = null
        setContent {
            theme { CredentialsTab(session.copy(credentials = listOf(first, first.copy(id = "motion-second"))),
                onDetailsChromeChange = { chrome = it }) }
        }
        val card = onNodeWithTag(WalletUiTestTags.credentialCard(first.id))
        waitUntil { onAllNodesWithTag(WalletUiTestTags.credentialCard(first.id)).fetchSemanticsNodes().isNotEmpty() }
        mainClock.autoAdvance = false
        card.performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
        frames(effects, 3)
        runOnIdle { assertNotNull(chrome).onClose() }
        frames(effects, 3)
        card.performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
        frames(effects, 3)
        runOnIdle { assertNotNull(chrome).onClose() }
        mainClock.autoAdvance = true
        waitForIdle()
        mainClock.advanceTimeBy(2_000)
        onAllNodesWithTag(WalletUiTestTags.CredentialDetailsScreen).assertCountEquals(0)
        onAllNodesWithTag(WalletUiTestTags.credentialDetails(first.id)).assertCountEquals(0)
        onNodeWithTag(WalletUiTestTags.credentialCard("motion-second")).assertExists().performClick()
        waitUntil { onAllNodesWithTag(WalletUiTestTags.credentialDetails("motion-second")).fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithTag(WalletUiTestTags.credentialDetails(first.id)).assertCountEquals(0)
    }

    fun reducedMotionSettlesTheCardStackWithoutWaitingForItsAnimationDuration() = motionTest { effects ->
        val first = WalletVisualFixtures.credentialSummary
        val cards = listOf(first, first.copy(id = "motion-second")).map { it.toCardDisplayData() }
        val expanded = mutableStateOf<String?>(null)
        setContent {
            theme {
                CompositionLocalProvider(LocalWalletVisualPreferences provides WalletVisualPreferences(reduceMotion = true, opaqueControls = true)) {
                    Box(Modifier.size(300.dp, 500.dp)) {
                        CredentialCardStack(cards, { expanded.value = it }, Modifier.testTag("motion-stack"), expanded.value)
                    }
                }
            }
        }
        val rest = onNodeWithTag("motion-stack").getUnclippedBoundsInRoot()
        mainClock.autoAdvance = false
        runOnIdle { expanded.value = first.id }
        frames(effects, 2)
        onAllNodesWithTag(WalletUiTestTags.credentialCard("motion-second")).assertCountEquals(0)
        val selected = onNodeWithTag("motion-stack").getUnclippedBoundsInRoot()
        assertTrue(selected.bottom - selected.top < rest.bottom - rest.top, "Reduced motion must settle the height immediately: $selected from $rest")
        runOnIdle { expanded.value = null }
        frames(effects, 2)
        assertEquals(rest, onNodeWithTag("motion-stack").getUnclippedBoundsInRoot())
        onNodeWithTag(WalletUiTestTags.credentialCard("motion-second")).assertExists()
    }

    // v2 keeps the test block and composition on separate schedulers. Drain only current
    // composition effects between frames, without advancing the frozen animation clock.
    private fun motionTest(block: suspend ComposeUiTest.(TestCoroutineScheduler) -> Unit) =
        StandardTestDispatcher().let { effects ->
            runComposeUiTest(effectContext = effects) { block(effects.scheduler) }
        }

    private suspend fun ComposeUiTest.frames(effects: TestCoroutineScheduler, count: Int) {
        repeat(count) { effects.runCurrent(); mainClock.advanceTimeByFrame(); waitForIdle() }
        effects.runCurrent()
        waitForIdle()
    }
}
