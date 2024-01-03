package id.walt.webwallet.service.dto

import kotlinx.serialization.Serializable

@Serializable
data class MarketPlaceDataTransferObject(
    val name: String,
    val url: String,
)
