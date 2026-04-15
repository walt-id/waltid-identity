package id.walt.issuer2.config

import id.walt.commons.config.WaltConfig
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.IssuerDisplay
import id.walt.sdjwt.SDMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class CredentialProfileConfig(
    val profileId: String,
    val name: String,
    val credentialConfigurationId: String,
    val issuerKey: JsonObject,
    val issuerDid: String? = null,
    val x5Chain: List<String>? = null,
    val credentialData: JsonObject,
    val mapping: JsonObject? = null,
    val selectiveDisclosure: SDMap? = null,
)

@Serializable
data class CredentialProfilesConfig(
    val profiles: List<CredentialProfileConfig> = emptyList(),
    val credentialConfigurations: Map<String, CredentialConfiguration> = emptyMap(),
    val issuerDisplay: List<IssuerDisplay>? = null,
) : WaltConfig()
