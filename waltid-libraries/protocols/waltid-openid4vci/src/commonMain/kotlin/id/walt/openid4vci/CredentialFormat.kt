package id.walt.openid4vci

/**
 * Credential format identifiers for the OpenID4VCI credential endpoint.
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
