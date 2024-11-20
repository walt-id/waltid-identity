package id.walt.webwallet.web.controllers.exchange.models.oid4vp

import kotlinx.serialization.Serializable

@Serializable
data class PrepareOID4VPRequest(
    val did: String,
    val presentationRequest: String,
    val selectedCredentialIdList: List<String>,
    val disclosures: Map<String, List<String>>? = null,
)