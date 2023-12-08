package id.walt.service.dto

import kotlinx.serialization.Serializable

@Serializable
data class MarketPlaceDataTransferObject(
    val name: String,
    val url: String,
)
