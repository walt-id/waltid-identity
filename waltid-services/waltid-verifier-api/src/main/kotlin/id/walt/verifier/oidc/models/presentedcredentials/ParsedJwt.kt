package id.walt.verifier.oidc.models.presentedcredentials

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ParsedJwt(
    val header: JsonObject,
    val payload: JsonObject,
)
