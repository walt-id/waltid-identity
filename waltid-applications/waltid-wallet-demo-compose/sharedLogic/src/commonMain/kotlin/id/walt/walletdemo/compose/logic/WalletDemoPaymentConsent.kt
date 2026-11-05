package id.walt.walletdemo.compose.logic

/** Resolved plain-text instructions; source resolution and validation belong to the wallet SDK. */
data class WalletDemoPaymentConsent(
    val revision: String,
    val locale: String,
    val title: String?,
    val securityHint: String?,
    val affirmativeAction: String,
    val denialAction: String?,
    val requiresUnsignedRequestWarning: Boolean,
    val fields: List<WalletDemoPaymentField>,
)

data class WalletDemoPaymentField(
    val label: String,
    val description: String?,
    val value: String,
    val placement: WalletDemoPaymentFieldPlacement,
    val path: List<String> = emptyList(),
)

enum class WalletDemoPaymentFieldPlacement { Prominent, Main, Details, Omitted }

sealed interface WalletDemoPaymentReview {
    data object NotRequired : WalletDemoPaymentReview
    data object Loading : WalletDemoPaymentReview
    data class Ready(val consent: WalletDemoPaymentConsent) : WalletDemoPaymentReview
    data class Blocked(val message: String) : WalletDemoPaymentReview
}

val WalletDemoPaymentReview.canConfirm: Boolean
    get() = this is WalletDemoPaymentReview.NotRequired || this is WalletDemoPaymentReview.Ready
val WalletDemoPaymentReview.consent: WalletDemoPaymentConsent?
    get() = (this as? WalletDemoPaymentReview.Ready)?.consent

/** Exact supported payment types, independent of credential format or localized display names. */
val ClaimGroup.isPaymentTransaction: Boolean
    get() = transactionType == "urn:eudi:sca:payment:1" ||
        transactionType == "org.waltid.transaction-data.payment-authorization"

/** Display emphasis for the legacy payment path; never substitutes for SDK-issued consent. */
private val ClaimItem.isPaymentSummary: Boolean
    get() {
        val path = pathComponents.drop(2).let { if (it.firstOrNull() == "payload") it.drop(1) else it }
        return pathComponents.getOrNull(1) == "details" &&
            path in listOf(listOf("amount"), listOf("currency"), listOf("payee"), listOf("payee", "name"))
    }

/** One presentation model for issuer-instructed and legacy payments. It grants no consent. */
data class WalletDemoPaymentDisplay(
    val id: String,
    val title: String?,
    val securityHint: String?,
    val requiresUnsignedRequestWarning: Boolean,
    val fields: List<WalletDemoPaymentDisplayField>,
    val technical: List<ClaimItem> = emptyList(),
)

data class WalletDemoPaymentDisplayField(
    val item: ClaimItem,
    val placement: WalletDemoPaymentFieldPlacement,
    val description: String? = null,
)

/** Presentation grouping never changes issuer placement or recovers fields from the raw payload. */
enum class PaymentDisplayGroup { Payee, Amount, Timing, Provider, Other }

val WalletDemoPaymentDisplayField.paymentPath: List<String>
    get() = if (item.pathComponents.getOrNull(1) == "details")
        item.pathComponents.drop(2).let { if (it.firstOrNull() == "payload") it.drop(1) else it }
    else item.pathComponents

val WalletDemoPaymentDisplayField.group: PaymentDisplayGroup
    get() = when (paymentPath.firstOrNull()) {
        "payee" -> PaymentDisplayGroup.Payee
        "amount", "currency" -> PaymentDisplayGroup.Amount
        "execution_date", "recurrence" -> PaymentDisplayGroup.Timing
        "pisp" -> PaymentDisplayGroup.Provider
        else -> PaymentDisplayGroup.Other
    }

val WalletDemoPaymentDisplayField.isLogo: Boolean get() = paymentPath == listOf("payee", "logo")
val WalletDemoPaymentDisplayField.isWebsite: Boolean get() = paymentPath == listOf("payee", "website")

fun WalletDemoPaymentReview.displays(transactions: List<ClaimGroup>): List<WalletDemoPaymentDisplay> = buildList {
    if (this@displays is WalletDemoPaymentReview.Ready) add(WalletDemoPaymentDisplay(
        id = consent.revision, title = consent.title, securityHint = consent.securityHint,
        requiresUnsignedRequestWarning = consent.requiresUnsignedRequestWarning,
        fields = consent.fields.mapIndexed { index, field -> WalletDemoPaymentDisplayField(
            ClaimItem(path = ClaimItemPath.topLevel("payment").indexedChild(index), label = field.label,
                value = field.displayValue(), rawValue = field.value, pathComponents = field.path), field.placement, field.description)
        },
    ))
    // An unresolved/rejected TS-12 consent must never fall back to transaction values.
    transactions.filter { this@displays == WalletDemoPaymentReview.NotRequired || it.transactionType != "urn:eudi:sca:payment:1" }
        .forEach { transaction ->
            val (values, technical) = transaction.items.partition { it.pathComponents.getOrNull(1) == "details" }
            add(WalletDemoPaymentDisplay(transaction.id, title = null, securityHint = null,
                requiresUnsignedRequestWarning = false,
                fields = values.map { WalletDemoPaymentDisplayField(it,
                    if (it.isPaymentSummary) WalletDemoPaymentFieldPlacement.Prominent else WalletDemoPaymentFieldPlacement.Main) },
                technical = technical))
        }
}

private val paymentTimestampPaths = setOf(
    listOf("date_time"), listOf("execution_date"),
    listOf("recurrence", "start_date"), listOf("recurrence", "end_date"),
)

/** Only known TS12 instants are interpreted; labels and arbitrary timestamp-looking text stay text. */
private fun WalletDemoPaymentField.displayValue(): DisplayValue =
    (if (path in paymentTimestampPaths) WalletDateTime.parseTimestamp(value) else null)
        ?.let(DisplayValue::InstantValue) ?: DisplayValue.Text(value)
