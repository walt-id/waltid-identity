package id.walt.webwallet.service.dto

import kotlinx.serialization.Serializable

@Serializable
data class WalletDataTransferObject(
    val address: String,
    val ecosystem: String,
)
