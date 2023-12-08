package id.walt.service.nft.fetchers

import id.walt.nftkit.services.FlowNFTMetadata
import id.walt.nftkit.services.FlowNftService
import id.walt.nftkit.utilis.Common
import id.walt.service.dto.NftConvertResult.Companion.toDataTransferObject
import id.walt.service.dto.NftDetailDataTransferObject
import id.walt.service.nft.converters.NftDetailConverterBase
import id.walt.service.nft.fetchers.parameters.TokenDetailParameter
import id.walt.service.nft.fetchers.parameters.TokenListParameter

class FlowDataFetcher(
    private val converter: NftDetailConverterBase<FlowNFTMetadata>,
) : DataFetcher {
    override fun all(parameter: TokenListParameter): List<NftDetailDataTransferObject> =
        FlowNftService.getAllNFTs(parameter.accountId, Common.getFlowChain(parameter.chain.lowercase())).map {
            converter.convert(it).toDataTransferObject(parameter.chain)
        }

    override fun byId(parameter: TokenDetailParameter): NftDetailDataTransferObject = FlowNftService.getNFTbyId(
        parameter.accountId,
        parameter.contract,
        parameter.collectionId ?: throw IllegalArgumentException("Missing collection-id parameter"),
        parameter.tokenId,
        Common.getFlowChain(parameter.chain.lowercase())
    ).let { converter.convert(it).toDataTransferObject(parameter.chain) }
}
