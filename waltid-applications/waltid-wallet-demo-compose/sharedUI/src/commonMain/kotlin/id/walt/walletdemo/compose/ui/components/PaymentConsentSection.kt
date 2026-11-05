package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalUriHandler
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
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
        val fields = payment.fields.filter { it.placement != WalletDemoPaymentFieldPlacement.Omitted }
        val payee = fields.filter { it.group == PaymentDisplayGroup.Payee }
        if (payee.isNotEmpty()) PaymentPayee(payee)
        val money = fields.filter { it.group == PaymentDisplayGroup.Amount && it.placement != WalletDemoPaymentFieldPlacement.Details }
            .sortedBy { if (it.paymentPath == listOf("amount")) 0 else 1 }
        if (money.isNotEmpty()) PaymentMoney(money)
        val remaining = fields.filter { it.group != PaymentDisplayGroup.Payee && it !in money }
        remaining.filter { it.placement == WalletDemoPaymentFieldPlacement.Prominent }.forEach { field ->
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                PaymentField(field, true, Modifier.fillMaxWidth().padding(16.dp))
            }
        }
        val main = remaining.filter { it.placement == WalletDemoPaymentFieldPlacement.Main }
        val timing = main.filter { it.group == PaymentDisplayGroup.Timing }
        if (timing.isNotEmpty()) {
            Text(stringResource(Res.string.payment_timing_title), style = MaterialTheme.typography.titleSmall)
            timing.forEach { PaymentField(it, false) }
        }
        main.filter { it.group != PaymentDisplayGroup.Timing }.forEach { PaymentField(it, false) }
        val details = remaining.filter { it.placement == WalletDemoPaymentFieldPlacement.Details }
        if (details.isNotEmpty()) MetadataDisclosure(stringResource(Res.string.payment_details_title), initiallyExpanded = false,
            modifier = Modifier.testTag("payment-details-toggle")) {
            val provider = details.filter { it.group == PaymentDisplayGroup.Provider }
            if (provider.isNotEmpty()) {
                Text(stringResource(Res.string.payment_provider_title), style = MaterialTheme.typography.titleSmall)
                provider.forEach { PaymentField(it, false) }
            }
            details.filter { it.group != PaymentDisplayGroup.Provider }.forEach { PaymentField(it, false) }
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
        PaymentValue(field, prominent)
        field.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun PaymentValue(field: WalletDemoPaymentDisplayField, prominent: Boolean, modifier: Modifier = Modifier) {
    val website = field.item.rawValue?.takeIf { field.isWebsite }?.let(::paymentWebsite)
    if (website != null) {
        val uriHandler = LocalUriHandler.current
        TextButton(onClick = { runCatching { uriHandler.openUri(website) } }, modifier = modifier, contentPadding = PaddingValues(0.dp)) {
            Text(website + " ↗")
        }
    } else ClaimValue(field.item.value, field.item.path, modifier.fillMaxWidth(),
        textStyle = if (prominent) MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
            else MaterialTheme.typography.bodyLarge)
}

@Composable
private fun PaymentPayee(fields: List<WalletDemoPaymentDisplayField>) {
    val logo = fields.singleOrNull { it.isLogo }
    val source = logo?.item?.rawValue
    var allowRemote by remember(source) { mutableStateOf(false) }
    val remoteLogo = source?.let(::paymentWebsite) != null
    val details = fields.filter { !it.isLogo && it.placement == WalletDemoPaymentFieldPlacement.Details }
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    fields.filter { !it.isLogo && it.placement != WalletDemoPaymentFieldPlacement.Details }.forEach {
                        PaymentField(it, it.placement == WalletDemoPaymentFieldPlacement.Prominent)
                    }
                }
                if (logo != null && source != null) PaymentLogo(source, logo.item.label, logo.description, allowRemote)
            }
            if (details.isNotEmpty() || remoteLogo && !allowRemote) MetadataDisclosure(
                stringResource(Res.string.payment_payee_details), initiallyExpanded = false,
                modifier = Modifier.testTag("payment-payee-details-toggle")) {
                details.forEach { PaymentField(it, false) }
                if (remoteLogo && !allowRemote) TextButton(onClick = { allowRemote = true }) {
                    Text(stringResource(Res.string.payment_load_logo))
                }
            }
        }
    }
}

@Composable
private fun PaymentMoney(fields: List<WalletDemoPaymentDisplayField>) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            fields.firstOrNull()?.let { Text(it.item.label, style = MaterialTheme.typography.labelLarge) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                fields.forEach {
                    Box(Modifier.width(IntrinsicSize.Max).alignByBaseline().testTag(WalletUiTestTags.claim(it.item.path.id))) {
                        PaymentValue(it, it.placement == WalletDemoPaymentFieldPlacement.Prominent)
                    }
                }
            }
            fields.forEach { it.description?.let { description -> Text(description, style = MaterialTheme.typography.bodyMedium) } }
        }
    }
}

@Composable
private fun PaymentLogo(source: String, label: String, description: String?, allowRemote: Boolean) {
    val bytes by key(source, allowRemote) {
        produceState<ByteArray?>(null) {
            value = loadPaymentLogo(source, allowRemote)?.takeIf(::paymentLogoHasSafeSize)
        }
    }
    val data = bytes ?: return
    val painter = rememberAsyncImagePainter(ImageRequest.Builder(LocalPlatformContext.current).data(data).size(128).build())
    val state by painter.state.collectAsState()
    if (state is AsyncImagePainter.State.Success) Column(Modifier.widthIn(max = 80.dp)) {
        Image(painter, contentDescription = label, Modifier.size(56.dp).testTag("payment-payee-logo"))
        description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

/** Check dimensions before decoding pixels. Unavailable or unsupported logos occupy no space. */
internal expect fun paymentLogoHasSafeSize(bytes: ByteArray): Boolean
