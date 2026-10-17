package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

internal val LocalWalletNavigationBackground = staticCompositionLocalOf<Color?> { null }

/** Sliding pages must paint their own surface so the parent cannot show through them. */
@Composable
internal fun Modifier.walletNavigationBackground(): Modifier =
    background(LocalWalletNavigationBackground.current ?: MaterialTheme.colorScheme.background)
