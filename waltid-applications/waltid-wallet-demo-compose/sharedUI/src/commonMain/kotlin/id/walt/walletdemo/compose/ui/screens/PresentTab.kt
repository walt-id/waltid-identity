package id.walt.walletdemo.compose.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
    onStartProximityPresentation: (() -> Unit)? = null,
    presentationContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val credentials = (state.session as? WalletSessionState.Ready)?.credentials.orEmpty()
    val preview = state.presentationPreview
    val error = state.presentationError

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
                    OutlinedButton(
                        onClick = start,
                        enabled = credentials.isNotEmpty() && state.presentationUrlEntryEnabled,
                        modifier = Modifier.testTag(WalletUiTestTags.ProximityStartButton),
                    ) {
                        Text(stringResource(Res.string.proximity_in_person_title))
                    }
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
