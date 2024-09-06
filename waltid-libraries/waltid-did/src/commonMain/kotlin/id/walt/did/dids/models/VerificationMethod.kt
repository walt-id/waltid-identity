package id.walt.did.dids.models

import kotlinx.serialization.json.JsonElement

data class VerificationMethod(
    val id: String,
    val type: VerificationMethodType,
    val controller: String,
    val customProperties: Map<String, JsonElement>,
)
