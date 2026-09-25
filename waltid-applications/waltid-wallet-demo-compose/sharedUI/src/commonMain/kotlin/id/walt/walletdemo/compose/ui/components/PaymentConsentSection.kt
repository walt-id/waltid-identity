package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.*
import kotlinx.coroutines.CancellationException

@Composable
internal fun rememberPaymentReview(
    review: WalletDemoSharingReview,
    selection: WalletDemoSharingSelection,
    prepare: (suspend (WalletDemoSharingSelection) -> WalletDemoPaymentConsent?)?,
): WalletDemoPaymentReview {
    val latestPrepare by rememberUpdatedState(prepare)
    val state = remember(review, selection.credentials, selection.disclosures) {
        mutableStateOf<WalletDemoPaymentReview>(if (prepare == null) WalletDemoPaymentReview.NotRequired else WalletDemoPaymentReview.Loading)
    }
    LaunchedEffect(state) {
        val resolve = latestPrepare ?: return@LaunchedEffect
        if (!review.hasCompleteCredentialSelection(selection.credentials)) return@LaunchedEffect
        state.value = try {
            resolve(selection)?.let { WalletDemoPaymentReview.Ready(it) } ?: WalletDemoPaymentReview.NotRequired
        } catch (cause: CancellationException) { throw cause
        } catch (cause: Exception) { WalletDemoPaymentReview.Blocked(cause.message ?: "Payment instructions are unavailable.") }
    }
    return state.value
}

@Composable
internal fun PaymentConsentSection(review: WalletDemoPaymentReview) {
    when (review) {
        WalletDemoPaymentReview.NotRequired -> Unit
        WalletDemoPaymentReview.Loading -> Text("Loading payment instructions…", modifier = Modifier.testTag("payment-consent-loading"))
        is WalletDemoPaymentReview.Blocked -> Text(review.message, color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("payment-consent-blocked"))
        is WalletDemoPaymentReview.Ready -> {
            val consent = review.consent
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.testTag("payment-consent")) {
                Text(consent.title ?: "Review payment", style = MaterialTheme.typography.headlineSmall)
                if (consent.requiresUnsignedRequestWarning) Text(
                    "This payment request is unsigned. Confirm only if you recognize the requester and approve the payment below.",
                    color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("payment-unsigned-warning"),
                )
                consent.securityHint?.let { Text(it, modifier = Modifier.testTag("payment-security-hint")) }
                consent.fields.filter { it.placement == WalletDemoPaymentFieldPlacement.Prominent }.forEach { field ->
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                        PaymentField(field, true, Modifier.fillMaxWidth().padding(16.dp))
                    }
                }
                consent.fields.filter { it.placement == WalletDemoPaymentFieldPlacement.Main }.forEach { PaymentField(it, false) }
                val details = consent.fields.filter { it.placement == WalletDemoPaymentFieldPlacement.Details }
                if (details.isNotEmpty()) {
                    var expanded by remember(consent.revision) { mutableStateOf(false) }
                    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.testTag("payment-details-toggle")) {
                        Text(if (expanded) "Hide payment details" else "Show payment details")
                    }
                    if (expanded) details.forEach { PaymentField(it, false) }
                }
            }
        }
    }
}

@Composable
private fun PaymentField(field: WalletDemoPaymentField, prominent: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(field.label, style = MaterialTheme.typography.labelLarge)
        Text(field.value, style = if (prominent) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
            fontWeight = if (prominent) FontWeight.SemiBold else FontWeight.Normal)
        field.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}
