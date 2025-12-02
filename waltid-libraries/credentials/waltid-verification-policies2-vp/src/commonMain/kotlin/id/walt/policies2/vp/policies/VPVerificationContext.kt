package id.walt.policies2.vp.policies

import id.walt.dcql.models.ClaimsQuery
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

sealed class VPVerificationRequest {
    abstract val vpToken: String
    abstract val expectedNonce: String
    abstract val expectedAudience: String?
}

@Serializable
data class BaseVerificationSessionContext(
    val isSigned: Boolean,
    val isEncrypted: Boolean,
    val responseMode: OpenID4VPResponseMode
)

@Serializable
@SerialName("jwt_vc_json")
data class JwtVcJsonVPVerificationRequest(
    /** vpJwt string - used by JwtVcJsonPresentation parser */
    override val vpToken: String,
    override val expectedNonce: String,
    override val expectedAudience: String?,
) : VPVerificationRequest()

@Serializable
@SerialName("dc+sd_jwt")
data class DcSdJwtVPVerificationRequest(
    /** full SD-JWT VC presentation string (core~disclosures~kb-jwt) - used by DcSdJwtPresentation parser */
    override val vpToken: String,
    override val expectedNonce: String,
    override val expectedAudience: String?,

    val originalClaimsQuery: List<ClaimsQuery>?
) : VPVerificationRequest()

@Serializable
@SerialName("mso_mdoc")
data class MsoMdocVPVerificationRequest(
    /** mdoc Base64Url String */
    override val vpToken: String,
    override val expectedNonce: String,
    override val expectedAudience: String?,

    val jwkThumbprint: String?
) : VPVerificationRequest()
