package id.walt.openid4vci

/**
 * OpenID4VCI 1.0 Credential Format identifiers.
 */
enum class CredentialFormat(val value: String) {
    JWT_VC_JSON("jwt_vc_json"),
    JWT_VC("jwt_vc"),
    SD_JWT_VC("dc+sd-jwt"),
    MSO_MDOC("mso_mdoc");

    companion object {
        fun fromValue(value: String): CredentialFormat? {
            return entries.find { it.value == value }
        }
    }
}
