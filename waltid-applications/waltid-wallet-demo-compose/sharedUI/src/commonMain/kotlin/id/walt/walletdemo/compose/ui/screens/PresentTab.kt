package id.walt.walletdemo.compose.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletDemoUiState
import id.walt.walletdemo.compose.logic.WalletRequestDrafts
import id.walt.walletdemo.compose.logic.WalletSessionState
import id.walt.walletdemo.compose.logic.presentationPreviewActionEnabled
import id.walt.walletdemo.compose.logic.presentationReviewEnabled
import id.walt.walletdemo.compose.logic.presentationUrlEntryEnabled
import id.walt.walletdemo.compose.ui.WalletUiTestTags
import id.walt.walletdemo.compose.ui.components.PresentationReviewSection
import id.walt.walletdemo.compose.ui.components.UrlActionSection

@Composable
internal fun PresentTab(
    state: WalletDemoUiState,
    requestDrafts: WalletRequestDrafts,
    onPresentationRequestUrlChange: (String) -> Unit,
    onPreview: () -> Unit,
    onStartNew: () -> Unit,
    onToggleCredential: (String) -> Unit,
    onCredentialClick: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val credentials = (state.session as? WalletSessionState.Ready)?.credentials.orEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag(WalletUiTestTags.PresentTabContent)
            .verticalScroll(scrollState)
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
            onClick = onPreview,
        )

        if (credentials.isEmpty()) {
            Text(
                "No credentials available",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (state.presentationCompleted) {
            OutlinedButton(
                onClick = onStartNew,
                modifier = Modifier.testTag(WalletUiTestTags.PresentationNewButton),
            ) {
                Text("New presentation")
            }
        }

        state.presentationPreview?.let { preview ->
            PresentationReviewSection(
                preview = preview,
                selectedCredentialIds = state.selectedPresentationCredentialIds,
                enabled = state.presentationReviewEnabled,
                readOnly = state.presentationCompleted,
                onToggleCredential = onToggleCredential,
                onCredentialClick = onCredentialClick,
                onSubmit = onSubmit,
                onCancel = onCancel,
            )
        }
    }
}
