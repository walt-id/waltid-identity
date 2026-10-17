package id.walt.walletdemo.compose.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
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
import id.walt.walletdemo.compose.ui.components.ExpandableMetadataCard
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

    fun disclosureAndSheetResizeTogetherIncludingReversal() = motionTest { effects ->
        val expanded = mutableStateOf(false)
        setContent {
            theme {
                WalletReviewHost(dismissEnabled = true, onDismiss = {}) {
                        ExpandableMetadataCard("Issuer", expanded.value, { expanded.value = !expanded.value },
                            modifier = Modifier.testTag("motion-metadata"), summary = { Text("Example issuer") }, details = { Box(Modifier.size(200.dp)) })
                }
            }
        }
        fun height() = onNodeWithTag("wallet.review.sheet").getUnclippedBoundsInRoot().let { it.bottom - it.top }
        fun contentHeight() = onNodeWithTag("motion-metadata").getUnclippedBoundsInRoot().let { it.bottom - it.top }
        val compact = height()
        val chrome = compact - contentHeight()
        fun assertSheetFollowsContent() {
            assertTrue(kotlin.math.abs((height() - contentHeight() - chrome).value) <= 1f,
                "The outer sheet must follow the animated content without a second resize: sheet=${height()}, content=${contentHeight()}, chrome=$chrome")
        }
        mainClock.autoAdvance = false
        runOnIdle { expanded.value = true }
        frames(effects, 5)
        val growing = height()
        assertTrue(growing > compact, "The sheet must follow the disclosure while it expands")
        assertSheetFollowsContent()
        frames(effects, 11)
        val full = height()
        assertTrue(full > growing, "Expansion must move through intermediate heights")
        frames(effects, 20)
        assertEquals(full, height(), "The outer sheet must not trail the content animation")
        runOnIdle { expanded.value = false }
        frames(effects, 5)
        val shrinking = height()
        assertTrue(shrinking > compact && shrinking < full, "Collapse must also animate its height")
        runOnIdle { expanded.value = true }
        repeat(40) { frames(effects, 1); assertSheetFollowsContent() }
        assertEquals(full, height(), "Reversal must finish at the current target")
    }

    fun reducedMotionSettlesDisclosuresImmediately() = motionTest { effects ->
        val expanded = mutableStateOf(false)
        setContent {
            theme {
                CompositionLocalProvider(LocalWalletVisualPreferences provides WalletVisualPreferences(reduceMotion = true)) {
                    ExpandableMetadataCard("Issuer", expanded.value, { expanded.value = !expanded.value },
                        modifier = Modifier.testTag("motion-metadata"), summary = { Text("Example issuer") },
                        details = { Box(Modifier.size(200.dp)) })
                }
            }
        }
        fun bounds() = onNodeWithTag("motion-metadata").getUnclippedBoundsInRoot()
        val compact = bounds()
        mainClock.autoAdvance = false
        runOnIdle { expanded.value = true }
        frames(effects, 3)
        val full = bounds()
        assertTrue(full.bottom > compact.bottom)
        frames(effects, 15)
        assertEquals(full, bounds())
        runOnIdle { expanded.value = false }
        frames(effects, 3)
        assertEquals(compact, bounds(), "Reduced motion must settle without the disclosure duration")
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
