package id.walt.issuer

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class SignRequest(
    val issuerKey: JsonObject,
    val vc: JsonElement
)
