package id.walt.webwallet.service.nft

import id.walt.webwallet.service.dto.*
import id.walt.webwallet.service.nft.fetchers.parameters.*

interface NftService {
    suspend fun getChains(ecosystem: String): List<ChainDataTransferObject>
    suspend fun getMarketPlace(parameter: TokenMarketPlaceParameter): MarketPlaceDataTransferObject?
    suspend fun getExplorer(parameter: ChainExplorerParameter): ChainExplorerDataTransferObject?
    suspend fun filterTokens(tenant: String, parameter: FilterParameter): List<FilterNftDataTransferObject>
    suspend fun getTokens(tenant: String, parameter: ListFetchParameter): List<NftDetailDataTransferObject>
    suspend fun getTokenDetails(tenant: String, parameter: DetailFetchParameter): NftDetailDataTransferObject
}
