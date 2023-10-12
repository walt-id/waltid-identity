package id.walt.oid4vc.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class JsonLDCredentialDefinition(
    @SerialName("@context") val context: List<JsonElement>? = null,
    val types: List<String>? = null,
)
