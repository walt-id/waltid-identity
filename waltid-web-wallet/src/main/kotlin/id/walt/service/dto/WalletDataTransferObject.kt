package id.walt.service.dto

import kotlinx.serialization.Serializable

@Serializable
data class WalletDataTransferObject(
    val address: String,
    val ecosystem: String,
)