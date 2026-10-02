package id.walt.walletdemo.compose.ui.components

import androidx.compose.runtime.Composable
import id.walt.walletdemo.compose.ui.WalletUiTestTags

@Composable
internal fun OfferReviewActions(
    requiresIssuerAuthentication: Boolean,
    acceptEnabled: Boolean,
    reviewEnabled: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    WalletActions(
        primary = WalletAction(if (requiresIssuerAuthentication) "Continue to sign in" else "Accept", onAccept,
            acceptEnabled, WalletUiTestTags.OfferAcceptButton,
            icon = if (requiresIssuerAuthentication) WalletSymbol.Next else WalletSymbol.Accept),
        secondary = WalletAction("Decline", onDecline, reviewEnabled, WalletUiTestTags.OfferDeclineButton, WalletSymbol.Decline),
    )
}
