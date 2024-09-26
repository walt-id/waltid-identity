package id.walt.webwallet.web.controllers.exchange.models.oid4vp

import id.walt.oid4vc.data.dif.PresentationSubmission
import kotlinx.serialization.Serializable

@Serializable
data class SubmitOID4VPRequest(
    val did: String,
    val presentationRequest: String,
    val presentationSubmission: PresentationSubmission,
    val presentedCredentialIdList: List<String>,
    val disclosures: Map<String, List<String>>? = null,
    val w3cJwtVpProof: String? = null,
    val ietfSdJwtVpProofs: List<IETFSdJwtVpTokenProof>? = null,
)