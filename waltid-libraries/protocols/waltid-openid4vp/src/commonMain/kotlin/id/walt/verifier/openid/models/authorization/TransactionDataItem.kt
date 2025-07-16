package id.walt.verifier.openid.models.authorization

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Represents the structure of a decoded item within the 'transaction_data' array.
 * See: Section 5.1 (transaction_data parameter)
 */
@Serializable
data class TransactionDataItem(
    val type: String,
    @SerialName("credential_ids")
    val credentialIds: List<String>,
    // Other transaction data type specific parameters would go here as JsonObject or specific fields
    val details: JsonObject? = null // For additional type-specific fields
)
