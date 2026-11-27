package id.walt.walletdemo.compose.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import id.walt.walletdemo.compose.logic.WalletDemoOfferPreview
import id.walt.walletdemo.compose.ui.resources.issuance_signing_selection
import id.walt.walletdemo.compose.ui.resources.issuance_signing_copies
import id.walt.walletdemo.compose.ui.resources.Res
import id.walt.walletdemo.compose.ui.resources.issuance_approve_signing
import org.jetbrains.compose.resources.stringResource
import id.walt.walletdemo.compose.ui.WalletUiTestTags

@Composable
internal fun OfferReviewActions(
    requiresIssuerAuthentication: Boolean,
    acceptEnabled: Boolean,
    reviewEnabled: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    awaitingSigning: Boolean = false,
) {
    val primaryLabel = when {
        awaitingSigning -> stringResource(Res.string.issuance_approve_signing)
        requiresIssuerAuthentication -> "Continue to sign in"
        else -> "Accept"
    }
    WalletActions(
        primary = WalletAction(primaryLabel, onAccept,
            acceptEnabled, WalletUiTestTags.OfferAcceptButton),
        secondary = WalletAction("Decline", onDecline, reviewEnabled, WalletUiTestTags.OfferDeclineButton),
    )
}

@Composable
internal fun SigningReviewContent(preview: WalletDemoOfferPreview, copies: Map<String, Int>) {
    Text(stringResource(Res.string.issuance_signing_selection, preview.issuer.display?.name ?: preview.issuer.credentialIssuer))
    for (id in preview.signingReview?.remainingSigningTargets.orEmpty()) {
        val credential = preview.offeredCredentials.firstOrNull { it.configurationId == id }
        Text(stringResource(Res.string.issuance_signing_copies, credential?.display?.name ?: id, copies[id] ?: 1))
    }
}
