package id.walt.openid4vci.metadata.issuer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Batch credential issuance metadata (OpenID4VCI 1.0).
 */
@Serializable
data class BatchCredentialIssuance(
    @SerialName("batch_size")
    val batchSize: Int,
) {
    init {
        require(batchSize >= 2) {
            "batch_credential_issuance.batch_size must be 2 or greater"
        }
    }
}
