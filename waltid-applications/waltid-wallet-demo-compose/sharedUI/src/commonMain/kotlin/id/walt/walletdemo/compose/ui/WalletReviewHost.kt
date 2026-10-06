package id.walt.walletdemo.compose.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.ui.components.LocalWalletNavigationBackground

/** Request state belongs above this host, so changing presentation cannot discard consent choices. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WalletReviewHost(
    presentation: WalletReviewPresentation,
    dismissEnabled: Boolean,
    onDismiss: (() -> Unit)?,
    content: @Composable (fillViewport: Boolean) -> Unit,
) {
    val canDismiss by rememberUpdatedState(dismissEnabled && onDismiss != null)
    val dismiss by rememberUpdatedState(onDismiss)
    // Busy flows consume Back. Otherwise the entry point decides whether leaving answers the
    // caller or merely returns to its selector; it is not implicitly the Cancel action.
    SystemBackHandler(enabled = !dismissEnabled || onDismiss != null || presentation == WalletReviewPresentation.Sheet) {
        if (canDismiss) dismiss?.invoke()
    }

    WalletDemoTheme {
        when (presentation) {
            WalletReviewPresentation.FullScreen -> Surface(
                modifier = Modifier.fillMaxSize().exportTestTagsForPlatformAutomation(),
                color = MaterialTheme.colorScheme.background,
            ) {
                Box(Modifier.fillMaxSize().safeDrawingPadding()) { content(true) }
            }
            WalletReviewPresentation.Sheet -> {
                val sheetState = rememberBottomSheetState(
                    initialValue = SheetValue.Hidden,
                    enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
                    confirmValueChange = { it != SheetValue.Hidden || canDismiss },
                )
                ModalBottomSheet(
                    modifier = Modifier.testTag("wallet.review.sheet")
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                            RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
                    onDismissRequest = { if (canDismiss) dismiss?.invoke() },
                    sheetState = sheetState,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrimColor = Color.Black.copy(alpha = 0.48f),
                    tonalElevation = 3.dp,
                    sheetGesturesEnabled = canDismiss,
                    properties = ModalBottomSheetProperties(
                        shouldDismissOnBackPress = canDismiss,
                        shouldDismissOnClickOutside = canDismiss,
                    ),
                ) {
                    // The sheet owns a separate window; export semantics in that window too.
                    Box(Modifier.exportTestTagsForPlatformAutomation()) {
                        CompositionLocalProvider(LocalWalletNavigationBackground provides MaterialTheme.colorScheme.surfaceContainer) {
                            content(false)
                        }
                    }
                }
            }
        }
    }
}
