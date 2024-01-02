package id.walt.webwallet.service.nft.converters

import id.walt.nftkit.services.PolkadotUniqueNft
import id.walt.webwallet.service.dto.NftConvertResult
import id.walt.webwallet.service.dto.TokenArt
import id.walt.webwallet.service.dto.TokenAttributes

class UniqueDetailConverter : NftDetailConverterBase<PolkadotUniqueNft>() {
    override fun convert(data: PolkadotUniqueNft): NftConvertResult = NftConvertResult(
        id = data.tokenId,
        art = TokenArt(url = data.metadata?.fullUrl),
        attributes = data.metadata?.attributes?.map {
            TokenAttributes(trait = it.name, value = it.value.toString())
        } ?: emptyList(),
        //...TODO
    )
}
