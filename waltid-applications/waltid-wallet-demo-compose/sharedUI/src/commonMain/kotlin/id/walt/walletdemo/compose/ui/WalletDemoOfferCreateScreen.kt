package id.walt.walletdemo.compose.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.ui.components.IssuanceResultContent
import id.walt.walletdemo.compose.ui.components.OfferReviewActions
import id.walt.walletdemo.compose.ui.components.OfferReviewSection
import id.walt.walletdemo.compose.ui.components.ReviewScaffold
import id.walt.walletdemo.compose.ui.components.WalletAction
import id.walt.walletdemo.compose.ui.components.WalletActions
import id.walt.walletdemo.compose.ui.components.WalletSymbol
import id.walt.walletdemo.compose.ui.components.WalletReviewNavigationHost
import id.walt.walletdemo.compose.ui.components.WalletScreenHeader
import id.walt.walletdemo.compose.ui.components.WalletIcon

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
    draft: WalletDemoOfferDraft? = null,
    onDone: () -> Unit = onDismiss,
    onResumeDeferred: (String) -> Unit = {},
    onRefresh: () -> Unit = {},
) {
    val preview = (state as? WalletDemoOfferCreateUiState.Review)?.preview
    val choices = draft ?: remember(preview) { WalletDemoOfferDraft() }
    val txCode = choices.transactionCode
    val copies = choices.copies
    val dismissEnabled = when (state) {
        is WalletDemoOfferCreateUiState.Review -> !state.submitting
        WalletDemoOfferCreateUiState.Loading -> true
        is WalletDemoOfferCreateUiState.WaitingForAuthorization -> !state.completing
        is WalletDemoOfferCreateUiState.Receipt -> !state.busy
        is WalletDemoOfferCreateUiState.Failure -> true
    }
    WalletReviewHost(presentation, dismissEnabled, onDismiss = {
        when (state) {
            is WalletDemoOfferCreateUiState.Receipt -> onDone()
            else -> onDismiss()
        }
    }) { fillViewport ->
        when (state) {
            WalletDemoOfferCreateUiState.Loading -> ReviewScaffold(fillViewport = fillViewport,
                header = {
                    WalletScreenHeader("Receive credentials") {
                        IconButton(onDecline) { WalletIcon(WalletSymbol.Decline, "Close request") }
                    }
                }) {
                OfferCreateLoadingContent()
            }
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
                WalletReviewNavigationHost(requestKey = offer.hashCode().toString(), offer = offer,
                    enabled = enabled, onClose = onDismiss.takeIf { enabled }) {
                    ReviewScaffold(
                        fillViewport = fillViewport,
                        header = {
                            WalletScreenHeader(state.title) {
                                IconButton(onClick = onDismiss, enabled = enabled) { WalletIcon(WalletSymbol.Decline, "Close request") }
                            }
                        },
                        feedback = state.errorMessage?.let { message -> { Text(message, color = MaterialTheme.colorScheme.error) } },
                        actions = {
                            OfferReviewActions(offer.requiresIssuerAuthentication, acceptEnabled, enabled, accept, onDecline)
                        },
                    ) {
                        OfferReviewSection(
                            preview = offer,
                            acceptEnabled = acceptEnabled,
                            reviewEnabled = enabled,
                            txCode = txCode,
                            onTxCodeChange = { choices.transactionCode = requirement?.normalizeInput(it) ?: it },
                            copies = copies,
                            onCopiesChange = { id, count ->
                                if (enabled && offer.offeredCredentials.any { it.configurationId == id }) {
                                    choices.copies = copies + (id to count.coerceIn(0, (offer.batchSize ?: 1).coerceAtLeast(1)))
                                }
                            },
                            onAccept = accept,
                            onDecline = onDecline,
                            showActions = false,
                        )
                    }
                }
            }
            is WalletDemoOfferCreateUiState.Receipt -> WalletReviewNavigationHost(
                requestKey = "provider-receipt", savedCredentials = state.saved,
                enabled = !state.busy, onClose = onDone.takeIf { !state.busy },
            ) {
                ReviewScaffold(
                    fillViewport = fillViewport,
                    header = {
                        WalletScreenHeader("Receiving result") {
                            IconButton(onClick = onDone, enabled = !state.busy) {
                                WalletIcon(WalletSymbol.Decline, "Close request")
                            }
                        }
                    },
                    feedback = state.refreshError?.let { message -> { Text(message, color = MaterialTheme.colorScheme.error) } },
                    actions = {
                        WalletActions(
                            WalletAction("Done", onDone, enabled = !state.busy, testTag = "wallet.provider.done"),
                            if (state.pending.isEmpty()) null else WalletAction("Refresh", onRefresh, enabled = !state.busy),
                        )
                    },
                ) {
                    IssuanceResultContent(state.receipt, state.saved, state.pending, state.busy, onResumeDeferred)
                }
            }
            is WalletDemoOfferCreateUiState.Failure -> ReviewScaffold(
                fillViewport = fillViewport,
                header = {
                    WalletScreenHeader("Unable to receive credentials") {
                        IconButton(onDecline) { WalletIcon(WalletSymbol.Decline, "Close request") }
                    }
                },
                feedback = { Text(state.message, color = MaterialTheme.colorScheme.error) },
            ) {
            }
            is WalletDemoOfferCreateUiState.WaitingForAuthorization -> ReviewScaffold(
                fillViewport = fillViewport,
                header = {
                    WalletScreenHeader("Receive credentials") {
                        IconButton(onCancelAuthorization, enabled = !state.completing) {
                            WalletIcon(WalletSymbol.Decline, "Close request")
                        }
                    }
                },
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().testTag(WalletUiTestTags.OfferAuthorizationSection),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        if (state.completing) "Finishing issuance…" else "Complete sign-in in your browser",
                        style = MaterialTheme.typography.bodyLarge,
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
