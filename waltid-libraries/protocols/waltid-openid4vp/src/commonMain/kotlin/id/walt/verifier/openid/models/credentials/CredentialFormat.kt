package id.walt.verifier.openid.models.credentials

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Standard Credential Format Identifiers for OpenID4VP.
 * See Appendix B of OpenID4VP spec.
 *
 * **This list can be extended by profiles.**
 *
 * THUS, THIS IS POSSIBLY **NON-EXHAUSTIVE**.
 */
@Serializable
enum class CredentialFormat {
    /** W3C VC as JWT */
    @SerialName("jwt_vc_json")
    JWT_VC_JSON,

    /** W3C VC with Data Integrity (JSON-LD) */
    @SerialName("ldp_vc")
    LDP_VC,

    /** ISO mdoc */
    @SerialName("mso_mdoc")
    MSO_MDOC,

    /** IETF SD-JWT VC (Draft 28 uses dc+sd-jwt) */
    @SerialName("dc+sd-jwt")
    DC_SD_JWT,

    /** AnonCreds (support pending) */
    @SerialName("ac_vp")
    AC_VP,

    // other common or profile-specific formats here
    // For truly custom/unknown formats, the original string field might be needed
    // or a custom serializer for this enum to handle unknown values.

    // For simplicity, this enum covers common ones.
}
