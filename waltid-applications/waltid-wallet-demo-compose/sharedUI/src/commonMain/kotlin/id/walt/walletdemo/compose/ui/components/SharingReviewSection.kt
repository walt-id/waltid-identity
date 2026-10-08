package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.*
import id.walt.walletdemo.compose.ui.WalletUiTestTags

/**
 * The wallet's single presentation-review surface, shared by every transport that can ask for a
 * credential.
 *
 * @param review What the user is being asked to share, already mapped off the transport's preview.
 * @param onReject Sends a protocol-level refusal to the requester. Pass null for transports with no
 * such message - the platform Digital Credentials APIs return a cancellation instead, and offering
 * both a Reject and a Cancel button there would promise the requester gets told two different things.
 */
@Composable
internal fun SharingReviewSection(
    review: WalletDemoSharingReview,
    selectedCredentialOptions: Set<WalletDemoPresentationCredentialSelection>,
    selectedDisclosureOptions: Set<WalletDemoPresentationDisclosureSelection>,
    selectionComplete: Boolean,
    enabled: Boolean,
    onToggleCredential: (WalletDemoPresentationCredentialSelection) -> Unit,
    onToggleDisclosure: (WalletDemoPresentationDisclosureSelection) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    onReject: (() -> Unit)? = null,
    readOnly: Boolean = false,
    compact: Boolean = false,
    showActions: Boolean = true,
    paymentReview: WalletDemoPaymentReview = WalletDemoPaymentReview.NotRequired,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(WalletUiTestTags.PresentationReview),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SharingRequestSections(if (paymentReview is WalletDemoPaymentReview.NotRequired) review.request else
            review.request.copy(transactionData = review.request.transactionData.filterNot { it.transactionType == "urn:eudi:sca:payment:1" }))
        PaymentConsentSection(paymentReview)

        WalletSection(title = if (compact) "Requested credentials" else "Select credentials to share") {
            if (review.credentialOptions.isEmpty()) {
                Text("No credentials available", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
            }
            review.credentialOptions.forEachIndexed { index, option ->
                if (index > 0) androidx.compose.material3.HorizontalDivider()
                SharingCredentialRow(
                    option = option,
                    selectedCredentialOptions = selectedCredentialOptions,
                    enabled = enabled,
                    readOnly = readOnly,
                    onToggleCredential = onToggleCredential,
                    hasAlternatives = review.credentialOptions.count { it.queryId == option.queryId } > 1,
                )
            }
        }
        SharingInformationSection(review, selectedCredentialOptions, selectedDisclosureOptions,
            enabled, readOnly, onToggleDisclosure)

        if (!readOnly && showActions) {
            SharingActionsRow(
                enabled = enabled,
                selectionComplete = selectionComplete,
                paymentReview = paymentReview,
                onSubmit = onSubmit,
                onCancel = onCancel,
                onReject = onReject,
            )
        }
    }
}
