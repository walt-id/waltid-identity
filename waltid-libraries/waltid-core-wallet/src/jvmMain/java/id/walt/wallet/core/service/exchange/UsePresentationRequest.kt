package id.walt.wallet.core.service.exchange

import kotlinx.serialization.Serializable

@Serializable
data class UsePresentationRequest(
    val did: String? = null,
    val presentationRequest: String,

    val selectedCredentials: List<String>, // todo: automatically choose matching
    val disclosures: Map<String, List<String>>? = null,
    val note: String? = null,
)

@Serializable
data class UsePresentationResponse(
    val ok: Boolean,
    val redirectUri: String? = null,
    val errorMessage: String? = null
)

data class PresentationRequestParameter(
    val did: String,
    val request: String,
    val selectedCredentials: List<String>,
    val disclosures: Map<String, List<String>>? = null,
    val note: String? = null,
)
