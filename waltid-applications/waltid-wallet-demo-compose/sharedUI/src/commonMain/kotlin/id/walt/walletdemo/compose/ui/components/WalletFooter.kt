package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeSourceRetention
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import id.walt.walletdemo.compose.ui.LocalWalletVisualPreferences

/** One control surface; only its own screen's content can contribute to the backdrop. */
@Composable
internal fun WalletFooter(
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    feedback: (@Composable () -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
) {
    val background = LocalWalletNavigationBackground.current ?: MaterialTheme.colorScheme.background
    val style = remember(background) {
        HazeBlurStyle {
            blurRadius(24.dp)
            noiseFactor(0f)
            backgroundColor(background)
            colorEffects(listOf(HazeColorEffect.tint(background.copy(alpha = .42f))))
            fallbackColorEffect(HazeColorEffect.tint(background))
        }
    }
    val preferences = LocalWalletVisualPreferences.current
    val backdrop = when {
        hazeState == null || preferences.opaqueControls -> Modifier.background(background)
        !preferences.blurSupported -> Modifier.background(background.copy(alpha = .82f))
        else -> Modifier.hazeBlur(
            input = HazeInput.Sources(hazeState, retention = HazeSourceRetention.ClearWhenUnavailable),
            style = style,
        )
    }
    Column(modifier.fillMaxWidth().testTag("wallet.footer").then(backdrop)
        // Empty areas belong to the footer, never to a control scrolling behind it.
        .pointerInput(Unit) { detectTapGestures {} }
        // Draw the same material through the bottom safe area; consume it once inside the surface.
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
        .padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        feedback?.invoke()
        actions?.invoke()
    }
}
