package id.walt.service.nft.converters

import id.walt.nftkit.services.NFTsAlchemyResult
import id.walt.service.dto.NftConvertResult
import id.walt.service.dto.TokenArt

class EvmDetailConverter : NftDetailConverterBase<NFTsAlchemyResult.NftTokenByAlchemy>() {
    override fun convert(data: NFTsAlchemyResult.NftTokenByAlchemy): NftConvertResult = NftConvertResult(
        id = data.id.tokenId,
        name = data.title,
        contract = data.contract.address,
        description = data.description,
        type = data.id.tokenMetadata.tokenType,
        art = TokenArt(imageData = data.metadata?.image_data, url = data.metadata?.image),
//        externalUrl = "https://polygonscan.com/address/${data.contract.address}"
    )
}