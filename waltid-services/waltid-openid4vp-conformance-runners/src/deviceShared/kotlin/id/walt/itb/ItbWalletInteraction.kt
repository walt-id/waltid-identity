package id.walt.itb

import id.walt.wallet2.consent.PaymentConsentException
import id.walt.wallet2.consent.PaymentConsentFailure
import io.ktor.http.Url
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URI

/** Inputs and credential IDs belong to the currently owned ITB session or its required issuance case. */
sealed interface ItbWalletInteraction {
    data class Offer(val url: URI, val transactionCode: String? = null) : ItbWalletInteraction
    data class CheckStatus(val credentialIds: List<String>, val expected: ItbExpectedStatus) : ItbWalletInteraction
    data class Presentation(
        val url: URI,
        val credentialId: String? = null,
        val attended: Boolean = false,
        val paymentReview: PaymentReview? = null,
    ) : ItbWalletInteraction
    data class DigitalCredentials(
        val endpoint: Url,
        val validationSession: String,
        val profile: String,
        val protocol: String,
        val payment: Payment? = null,
        val credentialId: String? = null,
    ) : ItbWalletInteraction

    data class DigitalIssuance(
        val endpoint: Url,
        val scenario: String,
        val credentialType: String,
        val protocol: String,
    ) : ItbWalletInteraction

    data class Payment(
        val attestationType: String, val merchant: String, val payeeId: String,
        val currency: String, val amount: String, val transactionId: String,
    ) {
        val review: PaymentReview get() = PaymentReview(merchant, currency, amount)
    }

    @Serializable
    data class PaymentReview(val merchant: String, val currency: String, val amount: String)
}

internal fun ItbWalletInteraction.DigitalCredentials.descriptorRequest() = buildJsonObject {
    put("profile", profile)
    put("sessionId", validationSession)
    payment?.let {
        put("attestation_type", it.attestationType)
        put("merchant", it.merchant)
        put("payee_id", it.payeeId)
        put("currency", it.currency)
        put("amount", it.amount)
        put("transaction_id", it.transactionId)
    }
}

enum class ItbExpectedStatus(val label: String) {
    CLEAR("Status clear"),
    INVALID("Invalid"),
}

@Serializable
data class ItbStatusObservation(val initial: String, val refreshed: String) {
    companion object {
        val labels = setOf("Status clear", "Invalid", "Not checked", "Checking status", "Status outdated", "Unavailable", "Other")
    }
}

@Serializable
data class ItbWalletResult(
    val issuedCredentialIds: List<String> = emptyList(),
    val statusObservations: List<ItbStatusObservation> = emptyList(),
) {
    init {
        require(issuedCredentialIds.size <= 20 && issuedCredentialIds.distinct().size == issuedCredentialIds.size)
        require(issuedCredentialIds.all { it.matches(Regex("[A-Za-z0-9._:-]{1,128}")) })
        require(statusObservations.size <= 2 &&
            statusObservations.all { it.initial in ItbStatusObservation.labels && it.refreshed in ItbStatusObservation.labels })
    }
}

class ItbWalletRejection(val code: String) : IllegalStateException("The ITB wallet operation failed ($code)") {
    companion object {
        private val paymentReviewFailures = PaymentConsentFailure.entries.associate { reason ->
            "demo_payment_review_${reason.name.lowercase()}" to requireNotNull(PaymentConsentException(reason).message)
        }

        fun blockedPaymentReview(message: String?) = ItbWalletRejection(
            paymentReviewFailures.entries.firstOrNull { it.value == message }?.key ?: "demo_payment_review_blocked",
        )

        fun describe(code: String?): String? = code?.let(paymentReviewFailures::get) ?: when (code) {
            "demo_payment_review_blocked" -> "The wallet blocked payment review."
            "demo_payment_review_loading" -> "The wallet timed out loading payment instructions."
            "demo_payment_review_missing" -> "The wallet did not display a payment review."
            "demo_payment_claim_review_failed" -> "The wallet could not update the information to share."
            "demo_payment_review_unexpected_payee" -> "The wallet review did not display the expected payee."
            "demo_payment_review_unexpected_total" -> "The wallet review did not display the expected amount and currency."
            "demo_payment_review_unsigned_request" -> "The wallet displayed an unsigned payment request warning."
            "demo_payment_review_expectation_missing" -> "The test case did not supply the expected payment details."
            "demo_payment_review_expectation_invalid" -> "The test case supplied an invalid expected amount."
            "demo_dc_picker_or_review_missing" -> "The Android credential sheet did not lead to wallet review."
            else -> null
        }
    }
}
