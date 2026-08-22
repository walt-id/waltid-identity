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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletDemoUiState
import id.walt.walletdemo.compose.logic.WalletRequestDrafts
import id.walt.walletdemo.compose.logic.acceptOfferEnabled
import id.walt.walletdemo.compose.logic.offerReviewEnabled
import id.walt.walletdemo.compose.logic.receiveActionEnabled
import id.walt.walletdemo.compose.logic.receiveUrlEntryEnabled
import id.walt.walletdemo.compose.ui.WalletUiTestTags
import id.walt.walletdemo.compose.ui.components.OfferReviewActions
import id.walt.walletdemo.compose.ui.components.OfferReviewSection
import id.walt.walletdemo.compose.ui.components.ReviewScaffold
import id.walt.walletdemo.compose.ui.components.UrlActionSection

@Composable
internal fun ReceiveTab(
    state: WalletDemoUiState,
    requestDrafts: WalletRequestDrafts,
    onOfferUrlChange: (String) -> Unit,
    onTxCodeChange: (String) -> Unit,
    onPreviewOffer: () -> Unit,
    onAcceptOffer: () -> Unit,
    onDeclineOffer: () -> Unit,
    onResumeDeferred: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val preview = state.offerPreview
    if (preview != null) {
        ReviewScaffold(
            modifier = modifier.testTag(WalletUiTestTags.ReceiveTabContent),
            actions = {
                OfferReviewActions(
                    requiresIssuerAuthentication = preview.requiresIssuerAuthentication,
                    acceptEnabled = state.acceptOfferEnabled,
                    reviewEnabled = state.offerReviewEnabled,
                    onAccept = onAcceptOffer,
                    onDecline = onDeclineOffer,
                )
            },
        ) {
            OfferReviewSection(
                preview = preview,
                acceptEnabled = state.acceptOfferEnabled,
                reviewEnabled = state.offerReviewEnabled,
                txCode = requestDrafts.txCode,
                onTxCodeChange = onTxCodeChange,
                onAccept = onAcceptOffer,
                onDecline = onDeclineOffer,
                showActions = false,
            )
            DeferredCredentials(state, onResumeDeferred)
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag(WalletUiTestTags.ReceiveTabContent)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        UrlActionSection(
            title = "Receive",
            value = requestDrafts.offerUrl,
            onValueChange = onOfferUrlChange,
            label = "Credential offer URL",
            buttonText = "Receive",
            enabled = state.receiveActionEnabled,
            inputEnabled = state.receiveUrlEntryEnabled,
            inputTestTag = WalletUiTestTags.OfferInput,
            buttonTestTag = WalletUiTestTags.ReceiveButton,
            scanButtonTestTag = WalletUiTestTags.OfferScanButton,
            onClick = onPreviewOffer,
        )
        DeferredCredentials(state, onResumeDeferred)
    }
}

@Composable
private fun DeferredCredentials(
    state: WalletDemoUiState,
    onResumeDeferred: (String) -> Unit,
) {
    if (state.deferredCredentials.isEmpty()) return
    Text("Pending credentials", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    state.deferredCredentials.forEach { pending ->
        OutlinedButton(
            onClick = { onResumeDeferred(pending.id) },
            enabled = !state.isAuthenticating,
        ) {
            Text("Check ${pending.credentialConfigurationId}")
        }
    }
}
