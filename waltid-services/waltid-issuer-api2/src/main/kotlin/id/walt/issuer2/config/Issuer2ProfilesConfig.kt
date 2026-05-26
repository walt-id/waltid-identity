package id.walt.issuer2.config

import id.walt.commons.config.WaltConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

data class Issuer2ProfilesConfig(
    val defaultIssuerKey: JsonObject? = null,
    val defaultIssuerDid: String? = null,
    val defaultIssuerX5chain: List<String> = emptyList(),
    val profiles: Map<String, CredentialProfileConfig> = emptyMap(),
) : WaltConfig()

@Serializable
data class CredentialProfileConfig(
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
