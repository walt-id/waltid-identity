package id.walt.webwallet.service.nft.fetchers

import id.walt.nftkit.services.AlgorandNftService
import id.walt.nftkit.services.AlgorandToken
import id.walt.nftkit.utilis.Common
import id.walt.webwallet.service.dto.NftConvertResult.Companion.toDataTransferObject
import id.walt.webwallet.service.dto.NftDetailDataTransferObject
import id.walt.webwallet.service.nft.converters.NftDetailConverterBase
import id.walt.webwallet.service.nft.fetchers.parameters.TokenDetailParameter
import id.walt.webwallet.service.nft.fetchers.parameters.TokenListParameter

class AlgorandDataFetcher(
    private val converter: NftDetailConverterBase<AlgorandToken>
) : DataFetcher {
    override fun all(parameter: TokenListParameter): List<NftDetailDataTransferObject> =
        AlgorandNftService.getAccountAssets(parameter.accountId, Common.getAlgorandChain(parameter.chain.uppercase())).map {
            converter.convert(it).toDataTransferObject(parameter.chain)
        }

    override fun byId(parameter: TokenDetailParameter): NftDetailDataTransferObject = AlgorandNftService.getToken(
        parameter.tokenId,
        Common.getAlgorandChain(parameter.chain.lowercase())
    ).let { converter.convert(it).toDataTransferObject(parameter.chain) }

}
