package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletDemoController
import id.walt.walletdemo.compose.logic.WalletDemoUiState
import id.walt.walletdemo.compose.logic.WalletInteractionKind
import id.walt.walletdemo.compose.logic.WalletInteractionState
import id.walt.walletdemo.compose.logic.WalletInteractionSuccessOutcome
import id.walt.walletdemo.compose.logic.WalletRequestSource
import id.walt.walletdemo.compose.logic.acceptOfferEnabled
import id.walt.walletdemo.compose.logic.offerReviewEnabled
import id.walt.walletdemo.compose.logic.presentationCredentialSelectionComplete
import id.walt.walletdemo.compose.logic.presentationReviewEnabled
import id.walt.walletdemo.compose.logic.toCredentialDetails
import id.walt.walletdemo.compose.ui.screens.CredentialDetailsScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WalletInteractionSheet(
    controller: WalletDemoController,
    state: WalletDemoUiState,
    onCredentialClick: (String) -> Unit,
) {
    val interaction = state.interaction
    val shouldShow = interaction !is WalletInteractionState.Idle &&
        interaction !is WalletInteractionState.LocalCancellation
    if (!shouldShow) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var reviewCredentialId by remember(state.presentationPreview?.previewHandle?.value) {
        mutableStateOf<String?>(null)
    }
    val reviewCredentialDetails = state.presentationPreview
        ?.credentialOptions
        .orEmpty()
        .map { it.toCredentialDetails() }
    val blocksDismissal = interaction is WalletInteractionState.Submitting ||
        interaction is WalletInteractionState.Declining
    ModalBottomSheet(
        onDismissRequest = {
            if (!blocksDismissal) {
                if (interaction is WalletInteractionState.Success) controller.finishInteraction()
                else controller.cancelInteraction()
            }
        },
        sheetState = sheetState,
    ) {
        when {
            reviewCredentialId != null -> reviewCredentialDetails
                .firstOrNull { it.summary.id == reviewCredentialId }
                ?.let { details ->
                    CredentialDetailsScreen(
                        details = details,
                        onBack = { reviewCredentialId = null },
                    )
                }
            interaction is WalletInteractionState.Success -> SuccessSheetContent(
                interaction = interaction,
                receivedCredentialId = state.lastReceivedCredentialIds.firstOrNull(),
                onDone = controller::finishInteraction,
                onViewCredential = { id ->
                    controller.finishInteraction()
                    onCredentialClick(id)
                },
            )
            state.offerPreview != null -> AddCredentialSheetContent(controller, state)
            state.presentationPreview != null -> ShareInformationSheetContent(
                controller = controller,
                state = state,
                onCredentialClick = { reviewCredentialId = it },
            )
            else -> {
                val kind = when (interaction) {
                    is WalletInteractionState.Capturing -> interaction.kind
                    is WalletInteractionState.Validating -> interaction.request.kind
                    is WalletInteractionState.Resolving -> interaction.request.kind
                    is WalletInteractionState.WrongRequestType -> interaction.expected
                    is WalletInteractionState.Failure -> interaction.kind
                    else -> WalletInteractionKind.Receive
                }
                val manualValue = when (kind) {
                    WalletInteractionKind.Receive -> state.requestDrafts.offerUrl
                    WalletInteractionKind.Present -> state.requestDrafts.presentationRequestUrl
                }
                RequestCaptureSheet(
                    interaction = interaction,
                    manualValue = manualValue,
                    onManualValueChange = { value ->
                        when (kind) {
                            WalletInteractionKind.Receive -> controller.updateOfferUrl(value)
                            WalletInteractionKind.Present -> controller.updatePresentationRequestUrl(value)
                        }
                    },
                    onCodeScanned = { controller.submitCapturedRequest(it, WalletRequestSource.Qr) },
                    onSubmitManual = { controller.submitCapturedRequest(manualValue, WalletRequestSource.Manual) },
                    onShowManual = controller::showManualEntry,
                    onShowScanner = controller::showScanner,
                    onSwitchFlow = controller::switchToDetectedRequest,
                    onRetry = controller::retryInteraction,
                    onCancel = controller::cancelInteraction,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun AddCredentialSheetContent(
    controller: WalletDemoController,
    state: WalletDemoUiState,
) {
    val preview = state.offerPreview ?: return
    Column(modifier = Modifier.fillMaxHeight(0.92f)) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Add this credential?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Review who is offering it and what it contains.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            OfferReviewSection(
                preview = preview,
                acceptEnabled = state.acceptOfferEnabled,
                reviewEnabled = state.offerReviewEnabled,
                txCode = state.requestDrafts.txCode,
                onTxCodeChange = controller::updateTxCode,
                onAccept = controller::acceptOffer,
                onDecline = controller::declineOffer,
                showActions = false,
            )
        }
        HorizontalDivider()
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (state.interaction is WalletInteractionState.Submitting) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Text("Adding credential…")
                }
            } else {
                OfferReviewActions(
                    acceptEnabled = state.acceptOfferEnabled,
                    reviewEnabled = state.offerReviewEnabled,
                    onAccept = controller::acceptOffer,
                    onDecline = controller::declineOffer,
                )
                TextButton(onClick = controller::cancelInteraction) { Text("Cancel locally") }
            }
        }
    }
}

@Composable
private fun ShareInformationSheetContent(
    controller: WalletDemoController,
    state: WalletDemoUiState,
    onCredentialClick: (String) -> Unit,
) {
    val preview = state.presentationPreview ?: return
    Column(modifier = Modifier.fillMaxHeight(0.94f)) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Share this information?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Requester details below are supplied by the verifier and do not establish trust.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            PresentationReviewSection(
                preview = preview,
                selectedCredentialOptions = state.selectedPresentationCredentialOptions,
                selectedDisclosureOptions = state.selectedPresentationDisclosureOptions,
                selectionComplete = state.presentationCredentialSelectionComplete(),
                enabled = state.presentationReviewEnabled,
                readOnly = state.presentationCompleted,
                onToggleCredential = controller::togglePresentationCredential,
                onToggleDisclosure = controller::togglePresentationDisclosure,
                onCredentialClick = onCredentialClick,
                onSubmit = controller::submitPresentation,
                onReject = controller::rejectPresentation,
                onCancel = controller::cancelPresentationReview,
                showActions = false,
            )
        }
        HorizontalDivider()
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (state.interaction is WalletInteractionState.Submitting ||
                state.interaction is WalletInteractionState.Declining
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Text(if (state.interaction is WalletInteractionState.Declining) "Declining request…" else "Sharing information…")
                }
            } else {
                PresentationReviewActions(
                    enabled = state.presentationReviewEnabled,
                    selectionComplete = state.presentationCredentialSelectionComplete(),
                    onSubmit = controller::submitPresentation,
                    onReject = controller::rejectPresentation,
                    onCancel = controller::cancelPresentationReview,
                )
            }
        }
    }
}

@Composable
private fun SuccessSheetContent(
    interaction: WalletInteractionState.Success,
    receivedCredentialId: String?,
    onDone: () -> Unit,
    onViewCredential: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            when (interaction.outcome) {
                WalletInteractionSuccessOutcome.CredentialAdded -> "Credential added"
                WalletInteractionSuccessOutcome.InformationShared -> "Information shared"
                WalletInteractionSuccessOutcome.OfferDeclined -> "Offer declined"
                WalletInteractionSuccessOutcome.PresentationRejected -> "Request rejected"
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(interaction.message, style = MaterialTheme.typography.bodyMedium)
        if (interaction.outcome == WalletInteractionSuccessOutcome.CredentialAdded && receivedCredentialId != null) {
            Button(onClick = { onViewCredential(receivedCredentialId) }) { Text("View credential") }
        }
        OutlinedButton(onClick = onDone) { Text("Done") }
    }
}
