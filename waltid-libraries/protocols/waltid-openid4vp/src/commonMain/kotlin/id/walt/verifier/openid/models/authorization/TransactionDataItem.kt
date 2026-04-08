package id.walt.verifier.openid.models.authorization

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the structure of a decoded item within the 'transaction_data' array.
 * See: Section 5.1 (transaction_data parameter)
 */
@Serializable
data class TransactionDataItem(
    val type: String,
    @SerialName("credential_ids")
    val credentialIds: List<String>,
    @SerialName("transaction_data_hashes_alg")
    val transactionDataHashesAlg: List<String>? = null,
    @SerialName("require_cryptographic_holder_binding")
    val requireCryptographicHolderBinding: Boolean? = null,
)
