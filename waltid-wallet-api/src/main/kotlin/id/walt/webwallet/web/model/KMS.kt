package id.walt.webwallet.web.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonObject

@Serializable
data class KMS @OptIn(ExperimentalSerializationApi::class) constructor(
    @JsonNames("kms")
    val data: Data? = null,
    val keyType: String,
) {
    @Serializable
    data class Data(
        val type: String,
        val config: JsonObject,
    )
}