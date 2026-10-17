package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.*
import id.walt.walletdemo.compose.ui.WalletUiTestTags

/** Rendering only; the host owns the review lifecycle and its single set of actions. */
@Composable
internal fun SharingReviewSection(
    review: WalletDemoSharingReview,
    information: List<SharingCredentialInformation>?,
    selectedCredentialOptions: Set<WalletDemoPresentationCredentialSelection>,
    selectedDisclosureOptions: Set<WalletDemoPresentationDisclosureSelection>,
    enabled: Boolean,
    onToggleCredential: (WalletDemoPresentationCredentialSelection) -> Unit,
    onToggleDisclosure: (WalletDemoPresentationDisclosureSelection) -> Unit,
    readOnly: Boolean = false,
    compact: Boolean = false,
    paymentReview: WalletDemoPaymentReview = WalletDemoPaymentReview.NotRequired,
    modifier: Modifier = Modifier,
) {
    val options = information.orEmpty()
    val groups = remember(options, selectedCredentialOptions, selectedDisclosureOptions) {
        options.informationToShare(selectedCredentialOptions, selectedDisclosureOptions)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(WalletUiTestTags.PresentationReview),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SharingRequestSections(review.request, paymentReview = paymentReview)

        if (information == null) Text("Loading credentials…", style = MaterialTheme.typography.bodyMedium)
        WalletSection(title = if (compact || review.credentialOptions.none(review::allowsCredentialSelection))
            "Requested credentials" else "Select credentials to share") {
            if (review.credentialOptions.isEmpty()) {
                Text("No credentials available", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
            }
            options.forEachIndexed { index, information ->
                if (index > 0) androidx.compose.material3.HorizontalDivider()
                SharingCredentialRow(
                    information = information,
                    selectedCredentialOptions = selectedCredentialOptions,
                    enabled = enabled,
                    readOnly = readOnly || !review.allowsCredentialSelection(information.option),
                    onToggleCredential = onToggleCredential,
                    hasAlternatives = review.credentialOptions.count { it.queryId == information.option.queryId } > 1,
                )
            }
        }
        if (information != null) SharingInformationSection(groups, selectedDisclosureOptions,
            enabled, readOnly, onToggleDisclosure)

    }
}

/** The host uses this same snapshot to gate confirmation until the reviewed values are rendered. */
@Composable
internal fun rememberSharingInformation(options: List<WalletDemoPresentationCredentialOption>): State<List<SharingCredentialInformation>?> =
    key(options) {
        produceState<List<SharingCredentialInformation>?>(null) {
            value = withContext(Dispatchers.Default) { options.map { it.toSharingInformation() } }
        }
    }
