package id.walt.webwallet.web.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class KMS(
    @SerialName("kms")
    val data: Data? = null,
    @SerialName("type")
    val keyType: String,
) {
    @Serializable
    data class Data(
        val type: String,
        val config: JsonObject,
    )
}