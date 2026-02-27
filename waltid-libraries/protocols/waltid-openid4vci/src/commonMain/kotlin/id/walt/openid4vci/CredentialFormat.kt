package id.walt.openid4vci

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * OpenID4VCI 1.0 Credential Format identifiers.
 */
@Serializable
enum class CredentialFormat(val value: String) {
    @SerialName("jwt_vc_json")
    JWT_VC_JSON("jwt_vc_json"),
    @SerialName("jwt_vc_json-ld")
    JWT_VC_JSON_LD("jwt_vc_json-ld"),
    @SerialName("ldp_vc")
    LDP_VC("ldp_vc"),
    @SerialName("jwt_vc")
    JWT_VC("jwt_vc"),
    @SerialName("dc+sd-jwt")
    SD_JWT_VC("dc+sd-jwt"),
    @SerialName("mso_mdoc")
    MSO_MDOC("mso_mdoc");

    companion object {
        fun fromValue(value: String): CredentialFormat? {
            return entries.find { it.value == value }
        }
    }
}
