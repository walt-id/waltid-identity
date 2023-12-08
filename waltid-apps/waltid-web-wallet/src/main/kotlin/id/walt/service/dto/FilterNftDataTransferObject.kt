package id.walt.service.dto

import kotlinx.serialization.Serializable

@Serializable
data class FilterNftDataTransferObject(
    val tokens: List<NftDetailDataTransferObject>,
    val owner: WalletDataTransferObject,
)
