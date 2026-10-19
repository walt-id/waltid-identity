@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.wallet2.transactionlog

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.serializers.LocalDateTimeIso8601Serializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * TS10 v1.2 section 3.1 transaction envelope, using the named detail objects from the data model.
 *
 * Each subtype contains only its corresponding non-null detail object.
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
    val time: LocalDateTime
    val transactionResult: TransactionResult
}

/** TS10 sections 3.1-3.2: a presentation transaction with its required presentation detail. */
@Serializable
@SerialName("Presentation")
data class PresentationTransaction(
    override val transactionIdentifier: String,
    @Serializable(with = LocalDateTimeIso8601Serializer::class)
    override val time: LocalDateTime,
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
    @Serializable(with = LocalDateTimeIso8601Serializer::class)
    override val time: LocalDateTime,
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
    @Serializable(with = LocalDateTimeIso8601Serializer::class)
    override val time: LocalDateTime,
    override val transactionResult: TransactionResult,
    val credentialDeletion: CredentialDeletion,
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

private fun Transaction.validateEnvelope() {
    require(transactionIdentifier.isNotBlank()) { "Transaction identifier must not be blank" }
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
