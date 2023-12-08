package id.walt.service.nft.converters

import id.walt.nftkit.services.NearNftMetadata
import id.walt.service.dto.NftConvertResult
import id.walt.service.dto.TokenArt

class NearDetailConverter : NftDetailConverterBase<NearNftCompoundMetadata>() {
    override fun convert(data: NearNftCompoundMetadata): NftConvertResult = NftConvertResult(
        id = data.metadata.token_id,
        name = data.metadata.metadata.title,
        contract = data.contract,
        description = data.metadata.metadata.description,
        art = TokenArt(url = data.metadata.metadata.media),
    )
}

data class NearNftCompoundMetadata(
    val metadata: NearNftMetadata,
    val contract: String,
)