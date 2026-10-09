package id.walt.walletdemo.compose.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.ui.components.LocalWalletNavigationBackground
import id.walt.walletdemo.compose.ui.components.WalletMotion

/** One modal container; request state and platform result handling belong to the caller. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WalletReviewHost(
    dismissEnabled: Boolean,
    onDismiss: (() -> Unit)?,
    sheetVisible: Boolean = true,
    onSheetHidden: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val canDismiss by rememberUpdatedState(dismissEnabled && onDismiss != null)
    val dismiss by rememberUpdatedState(onDismiss)
    val visible by rememberUpdatedState(sheetVisible)
    val hidden by rememberUpdatedState(onSheetHidden)
    // Busy flows consume Back. Otherwise the entry point decides whether leaving answers the
    // caller or merely returns to its selector; it is not implicitly the Cancel action.
    SystemBackHandler(enabled = true) {
        if (canDismiss) dismiss?.invoke()
    }

    WalletDemoTheme {
        val sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            confirmValueChange = { it != SheetValue.Hidden || canDismiss || !visible },
        )
        // Keep the host/content until cleanup completes and the exit animation finishes.
        LaunchedEffect(sheetVisible) {
            if (!sheetVisible) {
                sheetState.hide()
                if (!sheetState.isVisible) hidden()
            }
        }
        // Outer drawing modifiers use unanchored bounds; let the moving sheet own its chrome.
        val sheetMotion = if (LocalWalletVisualPreferences.current.reduceMotion) WalletMotion.reduced else WalletMotion.sheet
        MaterialTheme(motionScheme = sheetMotion) {
            ModalBottomSheet(
                modifier = Modifier.testTag("wallet.review.sheet"),
                onDismissRequest = { if (canDismiss) dismiss?.invoke() },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                scrimColor = Color.Black.copy(alpha = 0.48f),
                tonalElevation = 3.dp,
                sheetGesturesEnabled = canDismiss,
                // The page/footer owns the bottom inset so its surface reaches the sheet edge.
                contentWindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal) },
                properties = ModalBottomSheetProperties(
                    shouldDismissOnBackPress = canDismiss,
                    shouldDismissOnClickOutside = canDismiss,
                ),
            ) {
                // The sheet owns a separate window; export semantics in that window too.
                Box(Modifier.exportTestTagsForPlatformAutomation()) {
                    CompositionLocalProvider(LocalWalletNavigationBackground provides MaterialTheme.colorScheme.surfaceContainer) {
                        content()
                    }
                }
            }
        }
    }
}
