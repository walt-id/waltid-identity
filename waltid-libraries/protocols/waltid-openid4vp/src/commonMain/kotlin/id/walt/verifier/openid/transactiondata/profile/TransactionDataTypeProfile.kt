package id.walt.verifier.openid.transactiondata.profile

import id.walt.verifier.openid.transactiondata.DecodedTransactionData
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class TransactionDataTypeProfile(
    val type: String,
    val displayName: String,
    val mdocResponseNamespace: String = type,
    val applicableFormats: List<String>? = null,
    val requiredFields: List<String> = emptyList(),
    val mdocExtraFields: List<String> = emptyList(),
) {
    fun isApplicable(credentialFormat: String): Boolean =
        applicableFormats == null || credentialFormat in applicableFormats

    fun validate(decoded: DecodedTransactionData) {
        requiredFields.forEach { field ->
            require(field in decoded.details) { "transaction_data type '$type' requires '$field'" }
        }
    }

    fun mdocExtraResponseItems(decoded: DecodedTransactionData): Map<String, Any> = buildMap {
        mdocExtraFields.forEach { field ->
            decoded.details[field]?.let { value ->
                put(field, value.jsonPrimitive.content)
            }
        }
    }
}
