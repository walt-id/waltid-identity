package id.walt.walletdemo.compose.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import id.walt.walletdemo.compose.logic.receivedCredentials
import id.walt.walletdemo.compose.logic.toCredentialDetails
import id.walt.walletdemo.compose.ui.WalletUiTestTags
import id.walt.walletdemo.compose.ui.components.CredentialCard
import id.walt.walletdemo.compose.ui.components.OfferReviewSection
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
    onStartNew: () -> Unit,
    onResumeDeferred: (String) -> Unit,
    onCredentialClick: (String) -> Unit,
) {
    val receivedCredentials = state.receivedCredentials()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag(WalletUiTestTags.ReceiveTabContent)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (state.offerPreview == null) {
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
        } else {
            OfferReviewSection(
                preview = state.offerPreview!!,
                acceptEnabled = state.acceptOfferEnabled,
                reviewEnabled = state.offerReviewEnabled,
                txCode = requestDrafts.txCode,
                onTxCodeChange = onTxCodeChange,
                onAccept = onAcceptOffer,
                onDecline = onDeclineOffer,
            )
        }

        if (state.deferredCredentials.isNotEmpty()) {
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

        if (state.receiveCompleted) {
            OutlinedButton(
                onClick = onStartNew,
                modifier = Modifier.testTag(WalletUiTestTags.ReceiveNewButton),
            ) {
                Text("New receive")
            }
            Text("Received credentials", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            receivedCredentials.forEach { credential ->
                CredentialCard(
                    details = credential.toCredentialDetails(),
                    onClick = { onCredentialClick(credential.id) },
                )
            }
        }
    }
}
