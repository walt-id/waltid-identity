package id.walt.webwallet.service.issuers

import kotlinx.serialization.Serializable

@Serializable
data class IssuerDataTransferObject(
    val name: String,
    val description: String? = "no description",
    val uiEndpoint: String = "",
    val configurationEndpoint: String = "",
    val authorized: Boolean = false,
)
