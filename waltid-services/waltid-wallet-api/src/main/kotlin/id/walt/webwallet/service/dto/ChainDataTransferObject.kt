package id.walt.webwallet.service.dto

import kotlinx.serialization.Serializable

@Serializable
data class ChainDataTransferObject(
    val network: String,
    val ecosystem: String,
)
