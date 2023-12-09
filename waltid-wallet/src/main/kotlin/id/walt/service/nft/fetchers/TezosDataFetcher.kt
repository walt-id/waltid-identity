package id.walt.service.nft.fetchers

import id.walt.nftkit.services.NftService
import id.walt.nftkit.services.TezosNFTsTzktResult
import id.walt.nftkit.utilis.Common
import id.walt.service.dto.NftConvertResult.Companion.toDataTransferObject
import id.walt.service.dto.NftDetailDataTransferObject
import id.walt.service.nft.converters.NftDetailConverterBase
import id.walt.service.nft.fetchers.parameters.TokenDetailParameter
import id.walt.service.nft.fetchers.parameters.TokenListParameter

class TezosDataFetcher(
    private val converter: NftDetailConverterBase<TezosNFTsTzktResult>,
) : DataFetcher {
    override fun all(parameter: TokenListParameter): List<NftDetailDataTransferObject> =
        NftService.getAccountNFTs(Common.getChain(parameter.chain.uppercase()), parameter.accountId).tezosNfts?.map {
            converter.convert(it).toDataTransferObject(parameter.chain)
        } ?: emptyList()

    override fun byId(parameter: TokenDetailParameter): NftDetailDataTransferObject =
        all(TokenListParameter(parameter.chain, parameter.accountId)).filter {
            it.contract.equals(parameter.contract)
        }.firstOrNull {
            it.id == parameter.tokenId
        }
            ?: throw IllegalArgumentException("Token with id=${parameter.tokenId} on contract=${parameter.contract} not found.")
}