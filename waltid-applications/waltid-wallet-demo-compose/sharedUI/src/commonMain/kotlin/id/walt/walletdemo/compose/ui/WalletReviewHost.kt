package id.walt.walletdemo.compose.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.ui.components.LocalWalletNavigationBackground
import id.walt.walletdemo.compose.ui.components.WalletMotion
import id.walt.walletdemo.compose.ui.components.LocalWalletExpansionAnimations
import id.walt.walletdemo.compose.ui.components.WalletExpansionAnimations
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import id.walt.walletdemo.compose.ui.resources.Res
import id.walt.walletdemo.compose.ui.resources.wallet_sheet_handle
import org.jetbrains.compose.resources.stringResource

/** Sheet pages wrap their content; root and system-owned screens retain their viewport. */
internal val LocalWalletSheet = staticCompositionLocalOf { false }

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
        val expansions = remember { WalletExpansionAnimations() }
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
                // Reserve the safe area outside the moving surface. Applying top padding to
                // its content feeds the changing anchor back into measurement near full height.
                modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .testTag("wallet.review.sheet"),
                onDismissRequest = { if (canDismiss) dismiss?.invoke() },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                scrimColor = Color.Black.copy(alpha = 0.48f),
                tonalElevation = 3.dp,
                sheetGesturesEnabled = canDismiss,
                dragHandle = {
                    val label = stringResource(Res.string.wallet_sheet_handle)
                    Box(Modifier.padding(top = 8.dp, bottom = 4.dp).semantics { contentDescription = label }) {
                        Surface(Modifier.size(40.dp, 4.dp), shape = CircleShape,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)) {}
                    }
                },
                // The page/footer owns the bottom inset so its surface reaches the sheet edge.
                contentWindowInsets = { WindowInsets(0) },
                properties = ModalBottomSheetProperties(
                    shouldDismissOnBackPress = canDismiss,
                    shouldDismissOnClickOutside = canDismiss,
                ),
            ) {
                // The sheet owns a separate window; export semantics in that window too.
                // One resize owner for navigation, loading, and results. Nested navigators
                // report their destination size immediately while their pages slide and fade.
                // Even a snap animateContentSize defers its measurement by a frame. Remove
                // that modifier while a disclosure supplies the animated size itself.
                val resize = if (expansions.running) Modifier else Modifier.animateContentSize(
                    WalletMotion.sheetResize(LocalWalletVisualPreferences.current.reduceMotion))
                Box(resize.exportTestTagsForPlatformAutomation()) {
                    CompositionLocalProvider(LocalWalletSheet provides true,
                        LocalWalletExpansionAnimations provides expansions,
                        LocalWalletNavigationBackground provides MaterialTheme.colorScheme.surfaceContainer) {
                        content()
                    }
                }
            }
        }
    }
}
