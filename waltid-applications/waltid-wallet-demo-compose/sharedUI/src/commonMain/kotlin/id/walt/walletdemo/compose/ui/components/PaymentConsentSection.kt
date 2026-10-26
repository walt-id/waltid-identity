package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.*
import id.walt.walletdemo.compose.ui.WalletUiTestTags
import id.walt.walletdemo.compose.ui.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PaymentConsentSection(review: WalletDemoPaymentReview, transactions: List<ClaimGroup> = emptyList()) {
    when (review) {
        WalletDemoPaymentReview.Loading -> Text(stringResource(Res.string.payment_loading), modifier = Modifier.testTag("payment-consent-loading"))
        is WalletDemoPaymentReview.Blocked -> Text(review.message, color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("payment-consent-blocked"))
        else -> Unit
    }
    review.displays(transactions).forEach { payment ->
        key(payment.id) { PaymentReviewContent(payment) }
    }
}

/** Both payment sources use exactly the same hierarchy, field renderer and detail controls. */
@Composable
private fun PaymentReviewContent(payment: WalletDemoPaymentDisplay) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.testTag("payment-consent")) {
        Text(payment.title ?: stringResource(Res.string.payment_review_title), style = MaterialTheme.typography.headlineSmall)
        if (payment.requiresUnsignedRequestWarning) Text(stringResource(Res.string.payment_unsigned_warning),
            color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("payment-unsigned-warning"))
        payment.securityHint?.let { Text(it, modifier = Modifier.testTag("payment-security-hint")) }
        payment.fields.filter { it.placement == WalletDemoPaymentFieldPlacement.Prominent }.forEach { field ->
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                PaymentField(field, true, Modifier.fillMaxWidth().padding(16.dp))
            }
        }
        payment.fields.filter { it.placement == WalletDemoPaymentFieldPlacement.Main }.forEach { PaymentField(it, false) }
        val details = payment.fields.filter { it.placement == WalletDemoPaymentFieldPlacement.Details }
        if (details.isNotEmpty()) MetadataDisclosure(stringResource(Res.string.payment_details_title), initiallyExpanded = false,
            modifier = Modifier.testTag("payment-details-toggle")) {
            details.forEach { PaymentField(it, false) }
        }
        if (payment.technical.isNotEmpty()) MetadataDisclosure(stringResource(Res.string.credential_technical_details), initiallyExpanded = false) {
            payment.technical.forEach { ClaimValueRow(it) }
        }
    }
}

@Composable
private fun PaymentField(field: WalletDemoPaymentDisplayField, prominent: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.testTag(WalletUiTestTags.claim(field.item.path.id)), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(field.item.label, style = MaterialTheme.typography.labelLarge)
        ClaimValue(field.item.value, field.item.path, Modifier.fillMaxWidth(),
            textStyle = if (prominent) MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                else MaterialTheme.typography.bodyLarge)
        field.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}
