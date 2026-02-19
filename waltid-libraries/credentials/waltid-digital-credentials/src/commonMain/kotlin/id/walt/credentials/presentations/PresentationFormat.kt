package id.walt.credentials.presentations

import kotlinx.serialization.SerialName

/**
 * The specific formats for which the
 * OpenID4VP 1.0 specification provides
 * detailed rules and parameters in
 * its [Appendix B](https://openid.net/specs/openid-4-verifiable-presentations-1_0-final.html#appendix-B).
 */
@Suppress("EnumEntryName")
enum class PresentationFormat {
    jwt_vc_json,
    @SerialName("dc+sd-jwt")
    dc_sd_jwt,
    ldp_vc,
    mso_mdoc,
}
