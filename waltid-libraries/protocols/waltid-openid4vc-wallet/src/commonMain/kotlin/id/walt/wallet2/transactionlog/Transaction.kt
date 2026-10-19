@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.wallet2.transactionlog

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * TS10 v1.2 section 3.1 transaction envelope, using the named detail objects from the data model.
 *
 * Each subtype contains only its corresponding non-null detail object. [time] is UTC expressed as
 * YYYY-MM-DDTHH:mm:ss, without an offset, following TS10's examples. IDs and timestamps are supplied
 * by the caller. Serialize through Transaction.serializer() (or [TransactionLogCodec]) to include the
 * transactionType discriminator even when encoding a concrete transaction class.
 *
 * Invalid values are rejected with [IllegalArgumentException] on construction; [TransactionLogCodec]
 * converts these into SerializationException when decoding.
 *
 * This is a local log model, not the flattened export example in section 4.1 or a migration object.
 * Unavailable mandatory party metadata is nullable in the detail models; these partial entries
 * must not be represented as fully TS10-compliant export records.
 */
@Serializable
@JsonClassDiscriminator("transactionType")
sealed interface Transaction {
    val transactionIdentifier: String
    val time: String
    val transactionResult: TransactionResult
}

/** TS10 sections 3.1-3.2: a presentation transaction with its required presentation detail. */
@Serializable
@SerialName("Presentation")
data class PresentationTransaction(
    override val transactionIdentifier: String,
    override val time: String,
    override val transactionResult: TransactionResult,
    val presentation: Presentation,
) : Transaction {
    init {
        validateEnvelope(presentation.reasonOfNoncompletion)
    }
}

/** TS10 sections 3.1 and 3.5: an issuance transaction with its required issuance detail. */
@Serializable
@SerialName("CredentialIssuance")
data class CredentialIssuanceTransaction(
    override val transactionIdentifier: String,
    override val time: String,
    override val transactionResult: TransactionResult,
    val credentialIssuance: CredentialIssuance,
) : Transaction {
    init {
        validateEnvelope()
    }
}

/** TS10 sections 3.1 and 3.6: a deletion transaction with its required deletion detail. */
@Serializable
@SerialName("CredentialDeletion")
data class CredentialDeletionTransaction(
    override val transactionIdentifier: String,
    override val time: String,
    override val transactionResult: TransactionResult,
    val credentialDeletion: CredentialDeletion,
) : Transaction {
    init {
        validateEnvelope()
    }
}

/** TS10 sections 3.1 and 3.15: an additional operation with its required description detail. */
@Serializable
@SerialName("OtherTransaction")
data class OtherOperationTransaction(
    override val transactionIdentifier: String,
    override val time: String,
    override val transactionResult: TransactionResult,
    val otherTransaction: OtherTransaction,
) : Transaction {
    init {
        validateEnvelope()
    }
}

/** TS10 section 3.3 model only; the SDK does not currently generate wallet-to-wallet request entries. */
@Serializable
@SerialName("W2WPresentationRequest")
data class W2WPresentationRequestTransaction(
    override val transactionIdentifier: String,
    override val time: String,
    override val transactionResult: TransactionResult,
    val w2wPresentationRequest: W2WPresentationRequest,
) : Transaction {
    init {
        validateEnvelope()
    }
}

/** TS10 section 3.4 model only; the SDK does not currently generate wallet-to-wallet presentation entries. */
@Serializable
@SerialName("W2WPresentation")
data class W2WPresentationTransaction(
    override val transactionIdentifier: String,
    override val time: String,
    override val transactionResult: TransactionResult,
    val w2wPresentation: W2WPresentation,
) : Transaction {
    init {
        validateEnvelope(w2wPresentation.reasonOfNoncompletion)
    }
}

/** TS10 section 3.5 model only; the SDK does not currently distinguish or generate reissuance entries. */
@Serializable
@SerialName("CredentialReissuance")
data class CredentialReissuanceTransaction(
    override val transactionIdentifier: String,
    override val time: String,
    override val transactionResult: TransactionResult,
    val credentialIssuance: CredentialIssuance,
) : Transaction {
    init {
        validateEnvelope()
    }
}

/** TS10 section 3.7 model only; the SDK does not currently generate pseudonym-generation entries. */
@Serializable
@SerialName("PseudonymGeneration")
data class PseudonymGenerationTransaction(
    override val transactionIdentifier: String,
    override val time: String,
    override val transactionResult: TransactionResult,
    val pseudonymGeneration: PseudonymGeneration,
) : Transaction {
    init {
        validateEnvelope()
    }
}

/** TS10 section 3.8 model only; the SDK does not currently generate pseudonym-deletion entries. */
@Serializable
@SerialName("PseudonymDeletion")
data class PseudonymDeletionTransaction(
    override val transactionIdentifier: String,
    override val time: String,
    override val transactionResult: TransactionResult,
    val pseudonymDeletion: PseudonymDeletion,
) : Transaction {
    init {
        validateEnvelope()
    }
}

/**
 * TS10 section 3.9 model only; the SDK does not currently generate pseudonymous-authentication entries.
 * The discriminator follows section 3.1's PseudonymPresentation value, despite section 3.9's class name.
 */
@Serializable
@SerialName("PseudonymPresentation")
data class PseudonymousAuthenticationTransaction(
    override val transactionIdentifier: String,
    override val time: String,
    override val transactionResult: TransactionResult,
    val pseudonymousAuthentication: PseudonymousAuthentication,
) : Transaction {
    init {
        validateEnvelope(pseudonymousAuthentication.reasonOfNoncompletion)
    }
}

/** TS10 section 3.10 model only; the SDK does not currently generate signing/sealing certificate-issuance entries. */
@Serializable
@SerialName("CertificateIssuance")
data class CertificateIssuanceTransaction(
    override val transactionIdentifier: String,
    override val time: String,
    override val transactionResult: TransactionResult,
    val certificateIssuance: CertificateIssuance,
) : Transaction {
    init {
        validateEnvelope()
    }
}

/** TS10 section 3.11 model only; the SDK does not currently generate signing/sealing certificate-deletion entries. */
@Serializable
@SerialName("CertificateDeletion")
data class CertificateDeletionTransaction(
    override val transactionIdentifier: String,
    override val time: String,
    override val transactionResult: TransactionResult,
    val certificateDeletion: CertificateDeletion,
) : Transaction {
    init {
        validateEnvelope()
    }
}

/** TS10 section 3.12 model only; the SDK does not currently generate document-signing or sealing entries. */
@Serializable
@SerialName("SigningSealing")
data class SigningSealingTransaction(
    override val transactionIdentifier: String,
    override val time: String,
    override val transactionResult: TransactionResult,
    val signingSealing: SigningSealing,
) : Transaction {
    init {
        validateEnvelope()
    }
}

/** TS10 section 3.13 model only; the SDK does not currently generate relying-party data-deletion request entries. */
@Serializable
@SerialName("DataDeletionRequest")
data class DataDeletionRequestTransaction(
    override val transactionIdentifier: String,
    override val time: String,
    override val transactionResult: TransactionResult,
    val dataDeletionRequest: DataDeletionRequest,
) : Transaction {
    init {
        validateEnvelope()
    }
}

/** TS10 section 3.14 model only; the SDK does not currently generate data-protection-authority report entries. */
@Serializable
@SerialName("DPAReport")
data class DPAReportTransaction(
    override val transactionIdentifier: String,
    override val time: String,
    override val transactionResult: TransactionResult,
    val dpaReport: DPAReport,
) : Transaction {
    init {
        validateEnvelope()
    }
}

/** TS10 section 3.1 result values, preserved verbatim in JSON. */
@Serializable
enum class TransactionResult {
    Completed,
    NotCompleted,
}

/** TS10 section 3.16: a log is an array, including an empty array before any transactions. */
typealias TransactionLog = List<Transaction>

private val transactionTimePattern = Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}""")

private fun Transaction.validateEnvelope() {
    require(transactionIdentifier.isNotBlank()) { "Transaction identifier must not be blank" }
    requireValidTransactionTime(time)
}

internal fun requireValidTransactionTime(time: String) {
    require(time.matches(transactionTimePattern)) { "Transaction time must use YYYY-MM-DDTHH:mm:ss in UTC" }
    LocalDateTime.parse(time)
}

/** For transactions whose detail carries a reason: it is present if and only if the result is NotCompleted. */
private fun Transaction.validateEnvelope(reasonOfNoncompletion: String?) {
    validateEnvelope()
    require((transactionResult == TransactionResult.NotCompleted) == (reasonOfNoncompletion != null)) {
        "A noncompletion reason is required if and only if the transaction was not completed"
    }
}

internal fun requireValidNoncompletionReason(reason: String?) {
    require(reason == null || reason.isNotBlank()) { "Noncompletion reason must not be blank" }
}
