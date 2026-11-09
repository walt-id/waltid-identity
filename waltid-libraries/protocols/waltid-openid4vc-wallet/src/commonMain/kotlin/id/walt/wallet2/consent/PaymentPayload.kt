package id.walt.wallet2.consent

import id.walt.verifier.openid.transactiondata.Ts12Payment
import id.walt.verifier.openid.transactiondata.Ts12PaymentValidationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Display normalization never replaces the original transaction hash input. */
internal fun paymentValues(payload: JsonObject): Map<List<String>, String> = try {
    Ts12Payment.validate(payload).fields
} catch (error: Ts12PaymentValidationException) {
    consentFailure(if (error.unsupported) PaymentConsentFailure.UNSUPPORTED_PAYMENT else PaymentConsentFailure.INVALID_PAYMENT)
}

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
