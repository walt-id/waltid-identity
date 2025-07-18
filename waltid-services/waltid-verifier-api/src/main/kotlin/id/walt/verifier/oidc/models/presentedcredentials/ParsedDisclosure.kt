package id.walt.verifier.oidc.models.presentedcredentials

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ParsedDisclosure(
    val disclosure: String,
    val salt: String,
    val key: String,
    val value: JsonElement,
)
