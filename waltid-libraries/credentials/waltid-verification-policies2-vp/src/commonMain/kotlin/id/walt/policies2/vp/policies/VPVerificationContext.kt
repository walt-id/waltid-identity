package id.walt.policies2.vp.policies

import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import kotlinx.serialization.Serializable

/*sealed class VPVerificationRequest {
    abstract val base: BaseVerificationSessionContext
}*/

@Serializable
//data class BaseVerificationSessionContext(
data class VerificationSessionContext(
    val vpToken: String,
    val expectedNonce: String,
    val expectedAudience: String?,
    val expectedOrigins: List<String>?,

    val responseUri: String?,
    val responseMode: OpenID4VPResponseMode,

    val isSigned: Boolean,
    val isEncrypted: Boolean,
    val jwkThumbprint: String?
) {
    val isDcApi
        get() = responseMode in OpenID4VPResponseMode.DC_API_RESPONSES
}

/*
/** vpToken = vpJwt string - used by JwtVcJsonPresentation parser */
@Serializable
@SerialName("jwt_vc_json")
data class JwtVcJsonVPVerificationRequest(
    override val base: BaseVerificationSessionContext
) : VPVerificationRequest()

/** full SD-JWT VC presentation string (core~disclosures~kb-jwt) - used by DcSdJwtPresentation parser */
@Serializable
@SerialName("dc+sd_jwt")
data class DcSdJwtVPVerificationRequest(
    override val base: BaseVerificationSessionContext,

    val originalClaimsQuery: List<ClaimsQuery>?
) : VPVerificationRequest()

/** mdoc Base64Url String */
@Serializable
@SerialName("mso_mdoc")
data class MsoMdocVPVerificationRequest(
    override val base: BaseVerificationSessionContext,

    val jwkThumbprint: String?
) : VPVerificationRequest() {
    fun toMdocVerificationContext() = MdocVerificationContext(
        expectedNonce = base.expectedNonce,
        expectedAudience = base.expectedAudience,
        responseUri = base.responseUri,
        isEncrypted = base.isEncrypted,
        isDcApi = base.responseMode in OpenID4VPResponseMode.DC_API_RESPONSES,

        jwkThumbprint = jwkThumbprint
    )
}
*/
