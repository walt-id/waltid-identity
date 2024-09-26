package id.walt.webwallet.web.controllers.exchange.models.oid4vp

import id.walt.oid4vc.data.dif.PresentationSubmission
import kotlinx.serialization.Serializable

@Serializable
data class PrepareOID4VPResponse(
    val did: String,
    val presentationRequest: String,
    val selectedCredentialIdList: List<String>,
    val presentationSubmission: PresentationSubmission,
    val w3CJwtVpProofParameters: W3cJwtVpProofParameters? = null,
    val ietfSdJwtVpProofParameters: List<IETFSdJwtVpProofParameters>? = null,
)