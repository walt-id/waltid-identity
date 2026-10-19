package id.walt.wallet2.transactionlog

import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray

/**
 * JSON encoding of a [TransactionLog] (TS10 section 3.16).
 *
 * Decoding is lenient for interoperability with logs written by other wallets: unknown keys
 * (e.g. proprietary OtherTransaction attributes) are ignored, and unknown values of optional enum
 * fields such as the issuer type are read as null. Entries that are structurally or semantically
 * invalid, or have an unknown transactionType, fail the whole decode with a [SerializationException]
 * naming the entry index.
 */
object TransactionLogCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val serializer = ListSerializer(Transaction.serializer())

    fun encode(log: TransactionLog): String = json.encodeToString(serializer, log)

    fun decode(text: String): TransactionLog {
        val entries = try {
            json.decodeFromString(JsonArray.serializer(), text)
        } catch (e: IllegalArgumentException) {
            throw SerializationException("Transaction log must be a JSON array: ${e.message}", e)
        }
        return entries.mapIndexed { index, entry ->
            try {
                json.decodeFromJsonElement(Transaction.serializer(), entry)
            } catch (e: SerializationException) {
                throw SerializationException("Invalid transaction at index $index: ${e.message}", e)
            } catch (e: IllegalArgumentException) {
                throw SerializationException("Invalid transaction at index $index: ${e.message}", e)
            }
        }
    }
}
