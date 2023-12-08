package id.walt.service.nft.fetchers.parameters

import kotlinx.serialization.Serializable

@Serializable
data class TokenMarketPlaceParameter(
    val chain: String,
    val contract: String,
    val tokenId: String
)
