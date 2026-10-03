package id.walt.walletdemo.compose.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.isBusy
import id.walt.walletdemo.compose.logic.isError
import id.walt.walletdemo.compose.logic.statusText
import id.walt.walletdemo.compose.logic.receivedCredentials
import id.walt.walletdemo.compose.ui.components.*
import id.walt.walletdemo.compose.ui.resources.*
import org.jetbrains.compose.resources.stringResource
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
    onCopiesChange: (String, Int) -> Unit,
    onPreviewOffer: () -> Unit,
    onAcceptOffer: () -> Unit,
    onDeclineOffer: () -> Unit,
    onResumeDeferred: (String) -> Unit,
    onDone: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    fillViewport: Boolean = true,
) {
    val preview = state.offerPreview
    if (preview != null) {
        ReviewScaffold(
            fillViewport = fillViewport,
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
            if (state.externalFlow != null && state.isError) {
                SettingsNotice(state.statusText, error = true, modifier = Modifier.testTag(WalletUiTestTags.Status))
            }
            OfferReviewSection(
                preview = preview,
                acceptEnabled = state.acceptOfferEnabled,
                reviewEnabled = state.offerReviewEnabled,
                txCode = requestDrafts.txCode,
                onTxCodeChange = onTxCodeChange,
                copies = state.issuanceCopyCounts,
                onCopiesChange = onCopiesChange,
                onAccept = onAcceptOffer,
                onDecline = onDeclineOffer,
                showActions = false,
            )
        }
        return
    }

    if (state.issuanceReceipt != null || state.deferredCredentials.isNotEmpty()) {
        val pending = state.deferredCredentials.filter { state.issuanceReceipt?.pendingIds?.contains(it.id) ?: true }
        ReviewScaffold(fillViewport = fillViewport, modifier = modifier.testTag(WalletUiTestTags.ReceiveTabContent), actions = {
            WalletActions(WalletAction(stringResource(Res.string.issuance_done), onDone,
                enabled = !state.isBusy, testTag = "issuance-done", icon = WalletSymbol.Accept),
                secondary = pending.takeIf { it.isNotEmpty() }?.let {
                    WalletAction(stringResource(Res.string.issuance_refresh_status), onRefresh,
                        enabled = !state.isBusy, testTag = "issuance-refresh", icon = WalletSymbol.Retry)
                })
        }) {
            IssuanceResultContent(state.issuanceReceipt, state.receivedCredentials(), pending, state.isBusy, onResumeDeferred)
        }
        return
    }

    if (state.externalFlow != null) {
        ExternalFlowStatus(state, onRetry = onPreviewOffer, modifier = modifier, fillViewport = fillViewport)
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
    }
}
