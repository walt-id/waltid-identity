package id.walt.wallet2.consent

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlin.time.Instant

/** One ordinary payment only. Decoding/display never replaces the original transaction hash input. */
internal fun paymentValues(payload: JsonObject): Map<List<String>, String> {
    val required = setOf("transaction_id", "payee", "currency", "amount")
    if (!payload.keys.containsAll(required)) consentFailure(PaymentConsentFailure.INVALID_PAYMENT)
    if (!PAYMENT_FIELDS.containsAll(payload.keys)) consentFailure(PaymentConsentFailure.UNSUPPORTED_PAYMENT)
    val transactionId = payload.string("transaction_id")
    val payee = payload["payee"] as? JsonObject ?: consentFailure(PaymentConsentFailure.INVALID_PAYMENT)
    if (!payee.keys.containsAll(setOf("name", "id"))) consentFailure(PaymentConsentFailure.INVALID_PAYMENT)
    if (!setOf("name", "id", "logo", "website").containsAll(payee.keys)) consentFailure(PaymentConsentFailure.UNSUPPORTED_PAYMENT)
    val currency = payload.string("currency")
    val minorUnits = paymentCurrencyMinorUnits(currency) ?: consentFailure(PaymentConsentFailure.UNSUPPORTED_PAYMENT)
    val number = payload["amount"] as? JsonPrimitive ?: consentFailure(PaymentConsentFailure.INVALID_PAYMENT)
    if (number.isString) consentFailure(PaymentConsentFailure.INVALID_PAYMENT)
    val values = linkedMapOf(
        listOf("transaction_id") to transactionId,
        listOf("payee", "name") to payee.string("name"),
        listOf("payee", "id") to payee.string("id"),
        listOf("currency") to currency,
        listOf("amount") to exactPaymentAmount(number.content, minorUnits),
    )
    if ("date_time" in payload) {
        val timestamp = payload.string("date_time")
        try { Instant.parse(timestamp) } catch (_: IllegalArgumentException) {
            consentFailure(PaymentConsentFailure.INVALID_PAYMENT)
        }
        values[listOf("date_time")] = timestamp
    }
    for (name in listOf("logo", "website")) if (name in payee) {
        val uri = payee.string(name)
        // Display as issuer-labelled plain text; never fetch or execute a transaction-provided URI.
        if (!ABSOLUTE_URI.matches(uri)) consentFailure(PaymentConsentFailure.INVALID_PAYMENT)
        values[listOf("payee", name)] = uri
    }
    if ("pisp" in payload) {
        val pisp = payload["pisp"] as? JsonObject ?: consentFailure(PaymentConsentFailure.INVALID_PAYMENT)
        val fields = setOf("legal_name", "brand_name", "domain_name")
        if (!pisp.keys.containsAll(fields)) consentFailure(PaymentConsentFailure.INVALID_PAYMENT)
        if (pisp.keys != fields) consentFailure(PaymentConsentFailure.UNSUPPORTED_PAYMENT)
        fields.forEach { values[listOf("pisp", it)] = pisp.string(it) }
    }
    for (name in PAYMENT_FLAGS) if (name in payload) {
        val flag = payload[name] as? JsonPrimitive ?: consentFailure(PaymentConsentFailure.INVALID_PAYMENT)
        if (flag.isString || flag.booleanOrNull == null) consentFailure(PaymentConsentFailure.INVALID_PAYMENT)
        values[listOf(name)] = flag.content
    }
    return values
}

private val PAYMENT_FLAGS = setOf("amount_estimated", "amount_earmarked", "sct_inst")
private val PAYMENT_FIELDS = setOf("transaction_id", "payee", "currency", "amount", "date_time", "pisp") + PAYMENT_FLAGS
private val ABSOLUTE_URI = Regex("[A-Za-z][A-Za-z0-9+.-]*:[^\\s]+")

/** Expands decimal exponents without converting through floating point or rounding money. */
private fun exactPaymentAmount(text: String, minorUnits: Int): String {
    if (text.length > 128) consentFailure(PaymentConsentFailure.UNSUPPORTED_PAYMENT)
    val match = JSON_NUMBER.matchEntire(text) ?: consentFailure(PaymentConsentFailure.INVALID_PAYMENT)
    val negative = match.groupValues[1]
    val whole = match.groupValues[2]
    val fraction = match.groupValues[3]
    val exponent = match.groupValues[4].takeIf(String::isNotEmpty)?.toIntOrNull()
        ?: if (match.groupValues[4].isEmpty()) 0 else consentFailure(PaymentConsentFailure.UNSUPPORTED_PAYMENT)
    if (exponent !in -128..128) consentFailure(PaymentConsentFailure.UNSUPPORTED_PAYMENT)
    val digits = whole + fraction
    val point = whole.length + exponent
    val integerPart = when {
        point <= 0 -> "0"
        point >= digits.length -> digits + "0".repeat(point - digits.length)
        else -> digits.substring(0, point)
    }.trimStart('0').ifEmpty { "0" }
    val decimalPart = when {
        point <= 0 -> "0".repeat(-point) + digits
        point >= digits.length -> ""
        else -> digits.substring(point)
    }.trimEnd('0')
    if (decimalPart.length > minorUnits) consentFailure(PaymentConsentFailure.INVALID_PAYMENT)
    return negative + integerPart + if (decimalPart.isEmpty()) "" else ".$decimalPart"
}

private val JSON_NUMBER = Regex("(-?)(0|[1-9][0-9]*)(?:\\.([0-9]+))?(?:[eE]([+-]?[0-9]+))?")

internal fun JsonObject.string(name: String, failure: PaymentConsentFailure = PaymentConsentFailure.INVALID_PAYMENT): String {
    val value = this[name] as? JsonPrimitive ?: consentFailure(failure)
    if (!value.isString || value.content.isBlank()) consentFailure(failure)
    return value.content
}

internal fun String.codePointLength(): Int {
    var count = 0
    var index = 0
    while (index < length) {
        if (this[index] in '\uD800'..'\uDBFF' && index + 1 < length && this[index + 1] in '\uDC00'..'\uDFFF') index++
        index++
        count++
    }
    return count
}
