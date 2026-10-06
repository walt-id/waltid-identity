package id.walt.walletdemo.compose.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import id.walt.walletdemo.compose.ui.resources.*
import org.jetbrains.compose.resources.stringResource

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
    onDone: () -> Unit,
    onStartProximityPresentation: (() -> Unit)? = null,
    presentationContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    fillViewport: Boolean = true,
    feedback: (@Composable () -> Unit)? = null,
) {
    val credentials = (state.session as? WalletSessionState.Ready)?.credentials.orEmpty()
    val preview = state.presentationPreview
    val error = state.presentationError

    if (state.presentationCompleted) {
        ReviewScaffold(modifier = modifier.testTag("wallet.presentationResult"), fillViewport = fillViewport,
            actions = { id.walt.walletdemo.compose.ui.components.WalletActions(
                id.walt.walletdemo.compose.ui.components.WalletAction("Done", onDone, testTag = "wallet.presentationDone")) }) {
            val result = state.operation
            Text(when (result) {
                is id.walt.walletdemo.compose.logic.WalletOperationState.Succeeded -> result.message
                is id.walt.walletdemo.compose.logic.WalletOperationState.Failed -> result.message
                else -> "The sharing operation has ended."
            }, style = MaterialTheme.typography.bodyLarge,
                color = if (result is id.walt.walletdemo.compose.logic.WalletOperationState.Failed)
                    MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        }
        return
    }
    if (state.pendingPresentationContinuation != null) {
        ReviewScaffold(modifier = modifier, fillViewport = fillViewport, feedback = feedback) {
            Text("Finishing the response…", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    if (presentationContent != null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .testTag(WalletUiTestTags.PresentTabContent),
        ) {
            presentationContent()
        }
        return
    }

    if (preview != null) {
        ReviewScaffold(
            fillViewport = fillViewport,
            modifier = modifier.testTag(WalletUiTestTags.PresentTabContent),
            feedback = feedback,
            actions = {
                SharingActionsRow(
                    paymentReview = state.paymentReview,
                    enabled = state.presentationReviewEnabled,
                    selectionComplete = state.presentationCredentialSelectionComplete(),
                    onSubmit = onSubmit,
                    onCancel = onCancel,
                    onReject = onReject,
                    showCancelWithReject = false,
                )
            },
        ) {
            SharingReviewSection(
                paymentReview = state.paymentReview,
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

    if (state.externalFlow != null) {
        if (error != null) ReviewScaffold(modifier, fillViewport) {
            PresentationErrorSection(error, enabled = state.presentationReviewEnabled,
                onNotifyVerifier = onReject, onDismiss = onCancel)
        } else ExternalFlowStatus(state, onRetry = onPreview, modifier = modifier, fillViewport = fillViewport)
        return
    }

    ReviewScaffold(modifier = modifier.testTag(WalletUiTestTags.PresentTabContent),
        fillViewport = fillViewport, feedback = feedback) {
        UrlActionSection(
            title = stringResource(Res.string.proximity_online_request),
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

        onStartProximityPresentation?.let { start ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(Res.string.proximity_in_person_entry),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(Res.string.proximity_in_person_description),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    id.walt.walletdemo.compose.ui.components.WalletActions(
                        id.walt.walletdemo.compose.ui.components.WalletAction(
                            stringResource(Res.string.proximity_in_person_title), start,
                            enabled = credentials.isNotEmpty() && state.presentationUrlEntryEnabled,
                            testTag = WalletUiTestTags.ProximityStartButton,
                        ),
                    )
                }
            }
        }

        if (credentials.isEmpty()) {
            Text(
                stringResource(Res.string.proximity_no_credentials),
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
