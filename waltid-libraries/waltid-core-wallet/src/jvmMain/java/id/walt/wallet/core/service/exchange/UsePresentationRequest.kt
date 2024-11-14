package id.walt.wallet.core.service.exchange

import id.walt.wallet.core.utils.WalletCredential
import kotlinx.serialization.Serializable


@Serializable
data class UsePresentationRequest(
    val did: String? = null,
    val presentationRequest: String,

    val selectedCredentials: List<WalletCredential>, // todo: automatically choose matching
    val disclosures: Map<WalletCredential, List<String>>? = null,
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
    val selectedCredentials: List<WalletCredential>,
    val disclosures: Map<WalletCredential, List<String>>? = null,
    val note: String? = null,
)
