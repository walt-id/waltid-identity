package id.walt.issuer2.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class CredentialProfile(
    val profileId: String,
    val name: String,
    val credentialConfigurationId: String,
    val issuerKey: JsonObject,
    val issuerDid: String? = null,
    val credentialData: JsonObject,
    val mapping: JsonObject? = null,
    val selectiveDisclosure: JsonObject? = null,
    val idTokenClaimsMapping: Map<String, String>? = null,
    val mdocNamespacesDataMapping: Map<String, JsonObject>? = null,
    val x5Chain: List<String>? = null,
    val webhookUrl: String? = null,
)
