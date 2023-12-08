package id.walt.service.nft.converters

import id.walt.nftkit.services.TezosNFTsTzktResult
import id.walt.service.dto.NftConvertResult
import id.walt.service.dto.TokenArt

class TezosDetailConverter : NftDetailConverterBase<TezosNFTsTzktResult>() {
    override fun convert(data: TezosNFTsTzktResult): NftConvertResult = NftConvertResult(
        id = data.token.tokenId,
        name = data.token.metadata?.name,
        contract = data.token.contract.address,
        description = data.token.metadata?.description,
        type = data.token.standard,
        art = TokenArt(data.token.metadata?.image ?: data.token.metadata?.displayUri),
//        externalUrl = "https://tzkt.io/${data.token.contract.address}/operations"//TODO: "https://ghostnet.tzkt.io/${data.token.contract.address}/operations"
    )
}
