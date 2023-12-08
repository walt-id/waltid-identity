package id.walt.service.nft.fetchers.parameters

data class DetailFetchParameter(
    val chain: String,
    val walletId: String,
    val contract: String,
    val tokenId: String,
    val collectionId: String? = null,
)
