package id.walt.webwallet.web.controllers.exchange.models.oid4vci

import kotlinx.serialization.Serializable

@Serializable
data class PrepareOID4VCIRequest(
    val did: String? = null,
    val offerURL: String,
)