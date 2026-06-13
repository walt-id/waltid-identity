package id.walt.verifier.openid.models.authorization

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Represents the structure of a decoded item within the 'transaction_data' array.
 * See: OID4VP 1.0 §5.1 (transaction_data parameter) and §A.3.2 (SD-JWT VC profile).
 */
@Serializable
data class TransactionDataItem(
    val type: String,
    @SerialName("credential_ids")
    val credentialIds: List<String>,
    /**
     * OPTIONAL. Non-empty array of hash algorithm identifiers (IANA "Named Information Hash
     * Algorithm" registry), one of which MUST be used by the wallet to compute
     * `transaction_data_hashes` in the KB-JWT. If absent, `sha-256` is the default.
     */
    @SerialName("transaction_data_hashes_alg")
    val transactionDataHashesAlg: List<String>? = null,
    // Additional transaction data type specific parameters
    val details: JsonObject? = null
)
