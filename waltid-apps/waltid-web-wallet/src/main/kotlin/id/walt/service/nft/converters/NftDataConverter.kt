package id.walt.service.nft.converters

import id.walt.service.dto.NftConvertResult

interface NftDataConverter<in K, out T> {
    fun convert(data: K): T
}

abstract class NftDetailConverterBase<in T> : NftDataConverter<T, NftConvertResult>
abstract class NftListConverterBase<in T> : NftDataConverter<T, NftConvertResult>