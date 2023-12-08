package id.walt.service.nft.converters

import id.walt.nftkit.services.FlowNFTMetadata
import id.walt.service.dto.NftConvertResult
import id.walt.service.dto.TokenArt
import id.walt.service.dto.TokenAttributes

class FlowDetailConverter : NftDetailConverterBase<FlowNFTMetadata>() {
    override fun convert(data: FlowNFTMetadata): NftConvertResult = NftConvertResult(
        id = data.id,
        name = data.name,
//        contract = $auth.user.flowAccount :TODO,
        description = data.description,
        type = data.type,//??"FLIP-0636",
        art = TokenArt(url = data.thumbnail),
//        externalUrl = data.externalURL,
        attributes = data.traits?.traits?.mapNotNull {
            it.takeIf { !it.name.isNullOrEmpty() && !it.value.isNullOrEmpty() }?.let {
                TokenAttributes(trait = it.name!!, value = it.value!!)
            }
        } ?: emptyList(),
    )
}
