package id.walt.webwallet.service.nft.fetchers


import id.walt.nftkit.services.NFTsAlchemyResult
import id.walt.nftkit.services.NftService
import id.walt.nftkit.utilis.Common
import id.walt.webwallet.service.dto.NftConvertResult.Companion.toDataTransferObject
import id.walt.webwallet.service.dto.NftDetailDataTransferObject
import id.walt.webwallet.service.nft.converters.NftDetailConverterBase
import id.walt.webwallet.service.nft.fetchers.parameters.TokenDetailParameter
import id.walt.webwallet.service.nft.fetchers.parameters.TokenListParameter

class EvmDataFetcher(
    private val converter: NftDetailConverterBase<NFTsAlchemyResult.NftTokenByAlchemy>,
) : DataFetcher {
    override fun all(parameter: TokenListParameter): List<NftDetailDataTransferObject> =
        NftService.getAccountNFTs(Common.getChain(parameter.chain.uppercase()), parameter.accountId).evmNfts?.map {
            converter.convert(it).toDataTransferObject(parameter.chain)
        } ?: emptyList()

    override fun byId(parameter: TokenDetailParameter): NftDetailDataTransferObject =
        all(TokenListParameter(parameter.chain, parameter.accountId)).filter {
            it.contract.equals(parameter.contract)
        }.firstOrNull {
            it.id == parameter.tokenId
        } ?: throw IllegalArgumentException("Token with id=${parameter.tokenId} on contract=${parameter.contract} not found.")
}
