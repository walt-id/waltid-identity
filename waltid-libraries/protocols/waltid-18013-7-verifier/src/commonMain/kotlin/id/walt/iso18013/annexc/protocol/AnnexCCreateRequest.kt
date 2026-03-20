package id.walt.iso18013.annexc.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AnnexCRequestResponse(
    val protocol: String,
    val data: Data,
) {
    @Serializable
    data class Data(
        @SerialName("deviceRequest")
        val deviceRequest: String,
        @SerialName("encryptionInfo")
        val encryptionInfo: String,
    )
}

