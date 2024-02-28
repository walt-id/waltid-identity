package id.walt.webwallet.service.credentials

import kotlinx.serialization.json.JsonObject

data class Credential(
    val parsedCredential: JsonObject,
    val rawCredential: String
)
