package id.walt.verifier.openid.transactiondata

import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.verifier.openid.models.authorization.TransactionDataItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

private val knownParameterNames = setOf(
    "type",
    "credential_ids",
    "transaction_data_hashes_alg",
    "require_cryptographic_holder_binding",
)

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = false
}

@Serializable
data class DecodedTransactionData(
    val encoded: String,
    // Keeps the original JSON object intact for debugging/forward-compat checks without re-encoding.
    val rawJson: JsonObject,
    val transactionData: TransactionDataItem,
    // Carries extension fields (outside the known transaction_data parameters) for UI/business usage.
    val details: JsonObject,
)

fun decode(encoded: String): DecodedTransactionData {
    val rawJson = json.parseToJsonElement(encoded.decodeFromBase64Url().decodeToString()).jsonObject
    val transactionData = json.decodeFromJsonElement(TransactionDataItem.serializer(), rawJson)

    return DecodedTransactionData(
        encoded = encoded,
        rawJson = rawJson,
        transactionData = transactionData,
        details = JsonObject(rawJson.filterKeys { it !in knownParameterNames }),
    )
}

fun decodeList(transactionData: List<String>): List<DecodedTransactionData> = transactionData.map(::decode)
