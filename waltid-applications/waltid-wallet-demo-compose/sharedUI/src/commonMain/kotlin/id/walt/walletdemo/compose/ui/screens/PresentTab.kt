package id.walt.walletdemo.compose.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletDemoPresentationCredentialSelection
import id.walt.walletdemo.compose.logic.WalletDemoPresentationDisclosureSelection
import id.walt.walletdemo.compose.logic.WalletDemoUiState
import id.walt.walletdemo.compose.logic.WalletRequestDrafts
import id.walt.walletdemo.compose.logic.WalletSessionState
import id.walt.walletdemo.compose.logic.presentationCredentialSelectionComplete
import id.walt.walletdemo.compose.logic.presentationPreviewActionEnabled
import id.walt.walletdemo.compose.logic.presentationReviewEnabled
import id.walt.walletdemo.compose.logic.presentationUrlEntryEnabled
import id.walt.walletdemo.compose.logic.toSharingReview
import id.walt.walletdemo.compose.ui.WalletUiTestTags
import id.walt.walletdemo.compose.ui.components.PresentationErrorSection
import id.walt.walletdemo.compose.ui.components.ReviewScaffold
import id.walt.walletdemo.compose.ui.components.SharingActionsRow
import id.walt.walletdemo.compose.ui.components.SharingReviewSection
import id.walt.walletdemo.compose.ui.components.UrlActionSection

@Composable
internal fun PresentTab(
    state: WalletDemoUiState,
    requestDrafts: WalletRequestDrafts,
    onPresentationRequestUrlChange: (String) -> Unit,
    onPreview: () -> Unit,
    onToggleCredential: (WalletDemoPresentationCredentialSelection) -> Unit,
    onToggleDisclosure: (WalletDemoPresentationDisclosureSelection) -> Unit,
    onSubmit: () -> Unit,
    onReject: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val credentials = (state.session as? WalletSessionState.Ready)?.credentials.orEmpty()
    val preview = state.presentationPreview
    val error = state.presentationError

    if (preview != null) {
        ReviewScaffold(
            modifier = modifier.testTag(WalletUiTestTags.PresentTabContent),
            actions = {
                SharingActionsRow(
                    enabled = state.presentationReviewEnabled,
                    selectionComplete = state.presentationCredentialSelectionComplete(),
                    onSubmit = onSubmit,
                    onCancel = onCancel,
                    onReject = onReject,
                )
            },
        ) {
            SharingReviewSection(
                review = preview.toSharingReview(),
                selectedCredentialOptions = state.selectedPresentationCredentialOptions,
                selectedDisclosureOptions = state.selectedPresentationDisclosureOptions,
                selectionComplete = state.presentationCredentialSelectionComplete(),
                enabled = state.presentationReviewEnabled,
                readOnly = false,
                onToggleCredential = onToggleCredential,
                onToggleDisclosure = onToggleDisclosure,
                onSubmit = onSubmit,
                onReject = onReject,
                onCancel = onCancel,
                compact = false,
                showActions = false,
            )
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag(WalletUiTestTags.PresentTabContent)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        UrlActionSection(
            title = "Present",
            value = requestDrafts.presentationRequestUrl,
            onValueChange = onPresentationRequestUrlChange,
            label = "OpenID4VP request URL",
            buttonText = "Preview",
            enabled = state.presentationPreviewActionEnabled,
            inputEnabled = state.presentationUrlEntryEnabled,
            inputTestTag = WalletUiTestTags.PresentationInput,
            buttonTestTag = WalletUiTestTags.PresentButton,
            scanButtonTestTag = WalletUiTestTags.PresentationScanButton,
            onClick = onPreview,
        )

        if (credentials.isEmpty()) {
            Text(
                "No credentials available",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        error?.let {
            PresentationErrorSection(
                error = it,
                enabled = state.presentationReviewEnabled,
                onNotifyVerifier = onReject,
                onDismiss = onCancel,
            )
        }
    }
}
