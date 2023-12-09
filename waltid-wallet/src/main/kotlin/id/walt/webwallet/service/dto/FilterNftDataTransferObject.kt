package id.walt.webwallet.service.dto

import kotlinx.serialization.Serializable

@Serializable
data class FilterNftDataTransferObject(
    val tokens: List<NftDetailDataTransferObject>,
    val owner: WalletDataTransferObject,
)
