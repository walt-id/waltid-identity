package id.walt.issuer2.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The OAuth or OpenID4VCI error returned for a failed protocol request. It is published with failure
 * events and persisted for terminal credential failures.
 */
@Serializable
data class IssuanceSessionFailure(
    val error: String,
    @SerialName("error_description")
    val errorDescription: String? = null,
)
