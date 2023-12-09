package id.walt.service.nft

import id.walt.service.dto.*
import id.walt.service.nft.fetchers.parameters.*

interface NftService {
    suspend fun getChains(ecosystem: String): List<ChainDataTransferObject>
    suspend fun getMarketPlace(parameter: TokenMarketPlaceParameter): MarketPlaceDataTransferObject?
    suspend fun getExplorer(parameter: ChainExplorerParameter): ChainExplorerDataTransferObject?
    suspend fun filterTokens(parameter: FilterParameter): List<FilterNftDataTransferObject>
    suspend fun getTokens(parameter: ListFetchParameter): List<NftDetailDataTransferObject>
    suspend fun getTokenDetails(parameter: DetailFetchParameter): NftDetailDataTransferObject
}