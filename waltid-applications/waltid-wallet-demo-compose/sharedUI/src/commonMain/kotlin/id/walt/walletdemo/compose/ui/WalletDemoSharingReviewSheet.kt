package id.walt.walletdemo.compose.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletDemoPlatformPresentationLayout
import id.walt.walletdemo.compose.logic.WalletDemoSharingReview
import id.walt.walletdemo.compose.logic.WalletDemoSharingSelection
import id.walt.walletdemo.compose.logic.platformPresentationLayout

/**
 * Translucent-activity tray for an operating-system-invoked presentation.
 *
 * The review model chooses only the tray height. Selection, technical navigation, Back delegation,
 * and terminal outcomes remain owned by [WalletDemoSharingReviewScreen] and its platform host.
 */
@Composable
fun WalletDemoSharingReviewSheet(
    review: WalletDemoSharingReview,
    title: String,
    onSubmit: (WalletDemoSharingSelection) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onReject: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val layout = review.platformPresentationLayout()

    MaterialTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            DismissibleScrim(enabled = enabled, onDismiss = onDismiss)
            Surface(
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                tonalElevation = 2.dp,
                shadowElevation = 12.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(layout.heightFraction)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .testTag(WalletDemoSharingReviewTestTags.Sheet)
                    .exportTestTagsForPlatformAutomation(),
            ) {
                WalletDemoSharingReviewScreen(
                    review = review,
                    title = title,
                    enabled = enabled,
                    onSubmit = onSubmit,
                    onCancel = onCancel,
                    onReject = onReject,
                    // Keeping Back in the activity composition lets the review close its own
                    // technical destination before the provider host is dismissed.
                    onBackAtRoot = onDismiss,
                    modifier = Modifier
                        .fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun BoxScope.DismissibleScrim(
    enabled: Boolean,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .matchParentSize()
            .background(Color.Black.copy(alpha = 0.32f))
            .clickable(enabled = enabled, onClick = onDismiss),
    )
}

private val WalletDemoPlatformPresentationLayout.heightFraction: Float
    get() = when (this) {
        WalletDemoPlatformPresentationLayout.Compact -> 0.72f
        WalletDemoPlatformPresentationLayout.Expanded -> 0.94f
    }
