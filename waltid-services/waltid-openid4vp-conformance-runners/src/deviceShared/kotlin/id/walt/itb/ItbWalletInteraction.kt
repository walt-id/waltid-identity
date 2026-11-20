package id.walt.itb

import io.ktor.http.Url
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Inputs and credential IDs belong to the currently owned ITB session or its required issuance case. */
sealed interface ItbWalletInteraction {
    data class Offer(val url: Url, val transactionCode: String? = null) : ItbWalletInteraction
    data class CheckStatus(val credentialIds: List<String>, val expected: ItbExpectedStatus) : ItbWalletInteraction
    data class Presentation(val url: Url, val credentialId: String? = null, val attended: Boolean = false) : ItbWalletInteraction
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
    )
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

class ItbWalletRejection(val code: String) : IllegalStateException("The ITB wallet operation failed ($code)")
