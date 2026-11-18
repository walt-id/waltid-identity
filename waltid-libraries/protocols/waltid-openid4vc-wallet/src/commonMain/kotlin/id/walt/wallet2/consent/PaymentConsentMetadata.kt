package id.walt.wallet2.consent

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Instructions obtained through the authenticated payment-credential metadata chain.
 * Construction is restricted to [PaymentConsentResolver]; request-supplied text is never a source.
 * Payload coverage and language availability are checked when [resolve] receives a transaction.
 */
class PaymentConsentMetadata internal constructor(
    private val claims: JsonElement,
    private val catalogue: JsonElement,
    private val credentialClaims: JsonElement,
) {
    /**
     * Metadata-only platform projection. It contains issuer instructions, never credentials or keys.
     * A platform matcher must validate labels and coverage for the actual selected fields/payload.
     */
    val document: JsonObject by lazy { platformPaymentInstructions(claims, catalogue, credentialClaims) }

    /** Applies the same payment validation and localization rules as an app-owned review. */
    fun resolve(payload: JsonObject, preferredLocales: List<String>): PaymentConsent =
        localizePaymentConsent(claims, catalogue, payload, paymentValues(payload), preferredLocales)
}
