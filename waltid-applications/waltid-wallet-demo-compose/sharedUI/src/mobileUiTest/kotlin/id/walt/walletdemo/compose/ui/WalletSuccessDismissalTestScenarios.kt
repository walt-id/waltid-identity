package id.walt.walletdemo.compose.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.ui.components.rememberSuccessDismissal
import kotlin.test.assertEquals
import kotlinx.coroutines.test.StandardTestDispatcher

@OptIn(ExperimentalTestApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WalletSuccessDismissalTestScenarios {
    fun idleSuccessClosesOnceAfterFiveSeconds() {
        val dispatcher = StandardTestDispatcher()
        var closes = 0
        runComposeUiTest(effectContext = dispatcher) {
            mainClock.autoAdvance = false
            setContent { Result(enabled = true, active = true, onDone = { closes++ }) }
            dispatcher.scheduler.runCurrent()
            dispatcher.scheduler.advanceTimeBy(4_999)
            dispatcher.scheduler.runCurrent()
            assertEquals(0, closes)
            dispatcher.scheduler.advanceTimeBy(1)
            dispatcher.scheduler.runCurrent()
            assertEquals(1, closes)
            dispatcher.scheduler.advanceTimeBy(10_000)
            dispatcher.scheduler.runCurrent()
            assertEquals(1, closes)
        }
    }

    fun interactionKeepsTheResultOpen() {
        val dispatcher = StandardTestDispatcher()
        var closes = 0
        runComposeUiTest(effectContext = dispatcher) {
            setContent { Result(enabled = true, active = true, onDone = { closes++ }) }
            dispatcher.scheduler.advanceTimeBy(2_000)
            onNodeWithTag("result-touch").performTouchInput { click() }
            dispatcher.scheduler.runCurrent()
            waitForIdle()
            dispatcher.scheduler.advanceTimeBy(10_000)
            dispatcher.scheduler.runCurrent()
            assertEquals(0, closes)
        }
    }

    fun backgroundAndReplacementCancelThePreviousTimer() {
        val dispatcher = StandardTestDispatcher()
        val active = mutableStateOf(true)
        val key = mutableStateOf(1)
        var closes = 0
        runComposeUiTest(effectContext = dispatcher) {
            setContent { Result(key = key.value, enabled = true, active = active.value, onDone = { closes++ }) }
            dispatcher.scheduler.advanceTimeBy(3_000)
            runOnIdle { active.value = false }
            dispatcher.scheduler.runCurrent(); waitForIdle()
            dispatcher.scheduler.advanceTimeBy(10_000); dispatcher.scheduler.runCurrent()
            assertEquals(0, closes)
            runOnIdle { active.value = true }
            dispatcher.scheduler.runCurrent(); waitForIdle()
            dispatcher.scheduler.advanceTimeBy(3_000)
            runOnIdle { key.value = 2 }
            dispatcher.scheduler.runCurrent(); waitForIdle()
            dispatcher.scheduler.advanceTimeBy(3_000); dispatcher.scheduler.runCurrent()
            assertEquals(0, closes)
            dispatcher.scheduler.advanceTimeBy(2_000); dispatcher.scheduler.runCurrent()
            assertEquals(1, closes)
        }
    }

    fun screenReaderAndIncompleteResultsStayOpen() {
        val dispatcher = StandardTestDispatcher()
        val enabled = mutableStateOf(false)
        val reader = mutableStateOf(false)
        var closes = 0
        runComposeUiTest(effectContext = dispatcher) {
            setContent {
                CompositionLocalProvider(LocalWalletVisualPreferences provides WalletVisualPreferences(screenReaderEnabled = reader.value)) {
                    Result(enabled = enabled.value, active = true, onDone = { closes++ })
                }
            }
            dispatcher.scheduler.advanceTimeBy(10_000); dispatcher.scheduler.runCurrent()
            assertEquals(0, closes)
            runOnIdle { reader.value = true; enabled.value = true }
            dispatcher.scheduler.runCurrent(); waitForIdle()
            dispatcher.scheduler.advanceTimeBy(10_000); dispatcher.scheduler.runCurrent()
            assertEquals(0, closes)
        }
    }

    @Composable private fun Result(key: Int = 1, enabled: Boolean, active: Boolean, onDone: () -> Unit) {
        Box(Modifier.size(200.dp).then(rememberSuccessDismissal(key, enabled, onDone, active)).testTag("result-touch"))
    }
}
