package id.walt.issuer2.config

import id.walt.commons.config.WaltConfig
import id.walt.mdoc.dataelement.json.JsonObjectToCborMappingConfig
import id.walt.issuer2.notifications.IssuanceNotifications
import id.walt.sdjwt.SDMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

data class Issuer2ProfilesConfig(
    val defaultIssuerKey: JsonObject? = null,
    val defaultIssuerDid: String? = null,
    val defaultIssuerX5chain: List<String> = emptyList(),
    val defaultMdocIssuerX5chain: List<String> = emptyList(),
    val defaultHaipIssuerKey: JsonObject? = null,
    val defaultHaipIssuerX5chain: List<String> = emptyList(),
    val defaultHaipMdocIssuerX5chain: List<String> = emptyList(),
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
    val selectiveDisclosure: SDMap? = null,
    val idTokenClaimsMapping: Map<String, String>? = null,
    val mDocNameSpacesDataMappingConfig: Map<String, JsonObjectToCborMappingConfig>? = null,
    /**
     * OpenID4VP transaction_data types the issued key may sign. Each becomes a key in the mdoc MSO's
     * `KeyAuthorizations.dataElements`, authorizing the hash elements our presenter device-signs for
     * that type; presenting transaction data of an unlisted type is rejected.
     */
    val authorizedTransactionDataTypes: List<String>? = null,
    val x5Chain: List<String>? = null,
    val notifications: IssuanceNotifications? = null,
    val credentialStatus: JsonElement? = null,
)
