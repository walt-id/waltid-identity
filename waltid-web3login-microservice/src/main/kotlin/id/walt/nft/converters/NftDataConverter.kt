package id.walt.webwallet.service.nft.converters

import id.walt.webwallet.service.dto.NftConvertResult

interface NftDataConverter<in K, out T> {
    fun convert(data: K): T
}

abstract class NftDetailConverterBase<in T> : NftDataConverter<T, NftConvertResult>
abstract class NftListConverterBase<in T> : NftDataConverter<T, NftConvertResult>
