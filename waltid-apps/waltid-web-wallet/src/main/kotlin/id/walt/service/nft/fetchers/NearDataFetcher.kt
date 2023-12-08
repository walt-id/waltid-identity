package id.walt.service.nft.fetchers

import id.walt.nftkit.common.resolveContent
import id.walt.nftkit.services.NearNftService
import id.walt.nftkit.utilis.Common
import id.walt.service.dto.NftConvertResult.Companion.toDataTransferObject
import id.walt.service.dto.NftDetailDataTransferObject
import id.walt.service.nft.converters.NearNftCompoundMetadata
import id.walt.service.nft.converters.NftDetailConverterBase
import id.walt.service.nft.fetchers.parameters.TokenDetailParameter
import id.walt.service.nft.fetchers.parameters.TokenListParameter
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

class NearDataFetcher(
    private val converter: NftDetailConverterBase<NearNftCompoundMetadata>
) : DataFetcher {
    private val testnetLikelyNftsUrl = "https://testnet-api.kitwallet.app/account/%s/likelyNFTs"
    private val mainnetLikelyNftsUrl = "https://api.kitwallet.app/account/%s/likelyNFTs"

    override fun all(parameter: TokenListParameter): List<NftDetailDataTransferObject> = runBlocking {
        // TODO: inject http-client
        resolveContent(String.format(getLikelyNftsUrl(parameter.chain), parameter.accountId))
    }.let {
        Json.decodeFromString<List<String>>(it)
    }.flatMap { contract ->
        NearNftService.getNFTforAccount(
            parameter.accountId,
            contract,
            Common.getNearChain(parameter.chain.uppercase())
        ).map {
            NearNftCompoundMetadata(
                metadata = it,
                contract = contract
            )
        }
    }.map {
        converter.convert(it).toDataTransferObject(parameter.chain)
    }

    override fun byId(parameter: TokenDetailParameter): NftDetailDataTransferObject = NearNftService.getTokenById(
        parameter.contract,
        parameter.tokenId,
        Common.getNearChain(parameter.chain.lowercase())
    ).let {
        converter.convert(
            NearNftCompoundMetadata(
                metadata = it,
                contract = parameter.contract
            )
        ).toDataTransferObject(parameter.chain)
    }

    private fun getLikelyNftsUrl(chain: String) = when (chain.lowercase()) {
        "testnet" -> testnetLikelyNftsUrl
        "mainnet" -> mainnetLikelyNftsUrl
        else -> ""
    }
}
