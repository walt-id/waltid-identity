package id.walt.oid4vc.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GrantDetails(
    @SerialName("issuer_state") val issuerState: String? = null,
    @SerialName("pre-authorized_code") val preAuthorizedCode: String? = null,
    @SerialName("user_pin_required") val userPinRequired: Boolean? = null
)
