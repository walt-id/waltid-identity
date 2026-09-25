package id.walt.wallet2.consent

/** Why the wallet cannot prepare authoritative payment consent. */
enum class PaymentConsentFailure {
    UNTRUSTED_CREDENTIAL,
    METADATA_UNAVAILABLE,
    INVALID_METADATA,
    INTEGRITY_MISMATCH,
    UNSUPPORTED_SCHEMA,
    UNSUPPORTED_PAYMENT,
    INVALID_PAYMENT,
    MISSING_TRANSLATION,
    CONSENT_REQUIRED,
    STALE_CONSENT,
}

/** A typed failure with wallet-owned wording; remote metadata is never used as an error message. */
class PaymentConsentException(val reason: PaymentConsentFailure) : IllegalStateException(when (reason) {
    PaymentConsentFailure.UNTRUSTED_CREDENTIAL -> "The payment credential could not be authenticated."
    PaymentConsentFailure.METADATA_UNAVAILABLE -> "Payment instructions could not be retrieved."
    PaymentConsentFailure.INVALID_METADATA -> "The issuer's payment instructions are invalid."
    PaymentConsentFailure.INTEGRITY_MISMATCH -> "Payment instructions do not match the credential's integrity reference."
    PaymentConsentFailure.UNSUPPORTED_SCHEMA -> "This wallet does not support the specified payment schema."
    PaymentConsentFailure.UNSUPPORTED_PAYMENT -> "This wallet does not support this payment shape or currency."
    PaymentConsentFailure.INVALID_PAYMENT -> "The requested payment contains invalid values."
    PaymentConsentFailure.MISSING_TRANSLATION -> "Required payment instructions are unavailable in your preferred languages."
    PaymentConsentFailure.CONSENT_REQUIRED -> "Review the payment before authorizing it."
    PaymentConsentFailure.STALE_CONSENT -> "The payment selection changed. Review it again before authorizing."
})

/** Placement prescribed by the attestation type, resolved once in shared code. */
enum class PaymentFieldPlacement { PROMINENT, MAIN, DETAILS, OMITTED }

/** Validated plain-text field. Even omitted fields remain part of the signed transaction. */
class PaymentConsentField internal constructor(
    val path: List<String>,
    val label: String,
    val descriptionText: String?,
    val value: String,
    val placement: PaymentFieldPlacement,
)

/** Immutable localized issuer instructions. Missing optional title/denial label use wallet-owned UI text. */
class PaymentConsent internal constructor(
    val locale: String,
    val title: String?,
    val securityHint: String?,
    val affirmativeAction: String,
    val denialAction: String?,
    val fields: List<PaymentConsentField>,
)

internal const val TS12_PAYMENT_TYPE = "urn:eudi:sca:payment:1"
internal const val SCA_ATTESTATION_CATEGORY = "urn:eu:europa:ec:eudi:sua:sca"
internal fun consentFailure(reason: PaymentConsentFailure): Nothing = throw PaymentConsentException(reason)
