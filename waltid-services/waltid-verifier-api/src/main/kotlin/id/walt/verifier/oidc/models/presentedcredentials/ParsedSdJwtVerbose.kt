package id.walt.verifier.oidc.models.presentedcredentials

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ParsedSdJwtVerbose(
    val header: JsonObject,
    val fullPayload: JsonObject,
    val undisclosedPayload: JsonObject,
    val disclosures: Map<String, ParsedDisclosure>? = null,
)
