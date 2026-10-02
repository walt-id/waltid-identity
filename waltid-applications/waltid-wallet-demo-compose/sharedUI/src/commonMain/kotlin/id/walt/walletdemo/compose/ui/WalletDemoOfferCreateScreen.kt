package id.walt.walletdemo.compose.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.ui.components.OfferReviewActions
import id.walt.walletdemo.compose.ui.components.OfferReviewSection
import id.walt.walletdemo.compose.ui.components.ReviewScaffold
import id.walt.walletdemo.compose.ui.components.WalletAction
import id.walt.walletdemo.compose.ui.components.WalletActions
import id.walt.walletdemo.compose.ui.components.WalletSymbol

/**
 * Receiving review for platform hosts, using the same batch rows and actions as in-app receiving.
 * Acceptance forwards the explicit transaction code and copy selection; it does not execute the
 * protocol. Drafts remain above the presentation host so switching layout cannot reset them.
 */
@Composable
fun WalletDemoOfferCreateScreen(
    state: WalletDemoOfferCreateUiState,
    onAccept: (txCode: String?, copies: Map<String, Int>) -> Unit,
    onDecline: () -> Unit,
    onDismiss: () -> Unit,
    onCancelAuthorization: () -> Unit,
    presentation: WalletReviewPresentation = WalletReviewPresentation.FullScreen,
) {
    val preview = (state as? WalletDemoOfferCreateUiState.Review)?.preview
    var txCode by remember(preview) { mutableStateOf("") }
    var copies by remember(preview) { mutableStateOf(emptyMap<String, Int>()) }
    val dismissEnabled = when (state) {
        is WalletDemoOfferCreateUiState.Review -> !state.submitting
        WalletDemoOfferCreateUiState.Loading -> true
        is WalletDemoOfferCreateUiState.WaitingForAuthorization -> !state.completing
    }
    WalletReviewHost(presentation, dismissEnabled, onDismiss = {
        when (state) {
            is WalletDemoOfferCreateUiState.WaitingForAuthorization -> onCancelAuthorization()
            else -> onDismiss()
        }
    }) { fillViewport ->
        when (state) {
            WalletDemoOfferCreateUiState.Loading -> OfferCreateLoadingContent()
            is WalletDemoOfferCreateUiState.Review -> {
                val offer = state.preview
                val requirement = offer.transactionCode
                val enabled = !state.submitting
                val acceptEnabled = enabled && (requirement == null || requirement.accepts(txCode)) &&
                    offer.offeredCredentials.any { (copies[it.configurationId] ?: 1) > 0 }
                val accept = {
                    if (acceptEnabled) onAccept(
                        txCode.trim().ifBlank { null }?.takeIf { requirement != null }, copies,
                    )
                }
                ReviewScaffold(
                    fillViewport = fillViewport,
                    actions = {
                        OfferReviewActions(offer.requiresIssuerAuthentication, acceptEnabled, enabled, accept, onDecline)
                    },
                ) {
                    Text(state.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    OfferReviewSection(
                        preview = offer,
                        acceptEnabled = acceptEnabled,
                        reviewEnabled = enabled,
                        txCode = txCode,
                        onTxCodeChange = { txCode = requirement?.normalizeInput(it) ?: it },
                        copies = copies,
                        onCopiesChange = { id, count ->
                            if (enabled && offer.offeredCredentials.any { it.configurationId == id }) {
                                copies = copies + (id to count.coerceIn(0, (offer.batchSize ?: 1).coerceAtLeast(1)))
                            }
                        },
                        onAccept = accept,
                        onDecline = onDecline,
                        showActions = false,
                    )
                }
            }
            is WalletDemoOfferCreateUiState.WaitingForAuthorization -> ReviewScaffold(
                fillViewport = fillViewport,
                actions = if (state.completing) null else ({
                    WalletActions(WalletAction("Cancel", onCancelAuthorization, icon = WalletSymbol.Decline))
                }),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().testTag(WalletUiTestTags.OfferAuthorizationSection),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        if (state.completing) "Finishing issuance…" else "Complete sign-in in your browser",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (state.completing) "Saving your selected credentials…"
                        else "After you authorize with the issuer, the wallet will finish receiving your selection.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    CircularProgressIndicator(Modifier.size(32.dp).align(Alignment.CenterHorizontally))
                }
            }
        }
    }
}

@Composable
private fun OfferCreateLoadingContent() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(Modifier.size(32.dp))
        Text("Preparing credential offer…", color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium)
    }
}
