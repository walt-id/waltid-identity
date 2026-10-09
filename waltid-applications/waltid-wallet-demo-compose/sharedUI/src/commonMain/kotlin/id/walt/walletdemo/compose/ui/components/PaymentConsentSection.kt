package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.*
import id.walt.walletdemo.compose.ui.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PaymentConsentSection(review: WalletDemoPaymentReview) {
    when (review) {
        WalletDemoPaymentReview.NotRequired -> Unit
        WalletDemoPaymentReview.Loading -> Text(stringResource(Res.string.payment_loading), modifier = Modifier.testTag("payment-consent-loading"))
        is WalletDemoPaymentReview.Blocked -> Text(review.message, color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("payment-consent-blocked"))
        is WalletDemoPaymentReview.Ready -> {
            val consent = review.consent
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.testTag("payment-consent")) {
                Text(consent.title ?: stringResource(Res.string.payment_review_title), style = MaterialTheme.typography.headlineSmall)
                if (consent.requiresUnsignedRequestWarning) Text(
                    stringResource(Res.string.payment_unsigned_warning),
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
                    key(consent.revision) {
                        MetadataDisclosure(title = stringResource(Res.string.payment_details_title), initiallyExpanded = false,
                            modifier = Modifier.testTag("payment-details-toggle")) {
                            details.forEach { PaymentField(it, false) }
                        }
                    }
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
