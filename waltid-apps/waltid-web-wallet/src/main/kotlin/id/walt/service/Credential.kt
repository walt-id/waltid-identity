package id.walt.service

import kotlinx.serialization.json.JsonObject

data class Credential(
    val parsedCredential: JsonObject,
    val rawCredential: String
)
