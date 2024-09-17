package id.walt.oid4vc.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class CredentialDefinition (
    val credentialSubject: JsonObject? = null,
    val type: List<String>? = null,
)