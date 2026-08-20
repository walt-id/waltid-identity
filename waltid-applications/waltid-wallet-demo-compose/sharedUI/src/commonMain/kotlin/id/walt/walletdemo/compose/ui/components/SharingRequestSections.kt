package id.walt.walletdemo.compose.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import id.walt.walletdemo.compose.logic.WalletDemoReviewIslandKind
import id.walt.walletdemo.compose.logic.WalletDemoReviewSurfaceContext
import id.walt.walletdemo.compose.logic.WalletDemoSharingRequest
import id.walt.walletdemo.compose.logic.WalletDemoSharingReview
import id.walt.walletdemo.compose.logic.toReviewIslands
import id.walt.walletdemo.compose.ui.WalletUiTestTags

/**
 * Renders request-only content in a failed sharing flow with the same island grammar as the
 * interactive review. Available request facts never become proof of Verifier identity.
 */
@Composable
internal fun SharingRequestSections(request: WalletDemoSharingRequest, modifier: Modifier = Modifier) {
    ReviewIslandNavigationHost(
        reviewKey = request,
        islands = WalletDemoSharingReview(request = request, credentialOptions = emptyList())
            .toReviewIslands(WalletDemoReviewSurfaceContext.SelectedForSharing),
        modifier = modifier,
        islandModifier = { island ->
            when (island.kind) {
                WalletDemoReviewIslandKind.Verifier -> Modifier.testTag(WalletUiTestTags.PresentationVerifierSection)
                else -> Modifier
            }
        },
    )
}
