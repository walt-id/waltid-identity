package id.walt.service.nft.fetchers.parameters

data class TokenDetailParameter(
    val chain: String,
    val accountId: String,
    val contract: String,
    val tokenId: String,
    val collectionId: String? = null,
)
