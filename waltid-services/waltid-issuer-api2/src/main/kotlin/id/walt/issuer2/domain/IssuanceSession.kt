package id.walt.issuer2.domain

import id.walt.mdoc.dataelement.json.JsonObjectToCborMappingConfig
import id.walt.openid4vci.offers.AuthenticationMethod
import id.walt.openid4vci.offers.CredentialOffer
import id.walt.sdjwt.SDMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.time.Instant

@Serializable
enum class IssuanceSessionStatus {
    ACTIVE,
    TOKEN_REQUESTED,
    CREDENTIAL_ISSUED,
    SUCCESSFUL,
    UNSUCCESSFUL,
    EXPIRED,
}

@Serializable
data class IssuanceSession(
    val sessionId: String,
    val profileId: String,
    val authenticationMethod: AuthenticationMethod,
    val credentialConfigurationId: String,
    val credentialData: JsonObject,
    val mapping: JsonObject? = null,
    val selectiveDisclosure: SDMap? = null,
    val idTokenClaimsMapping: Map<String, String>? = null,
    val mDocNameSpacesDataMappingConfig: Map<String, JsonObjectToCborMappingConfig>? = null,
    val x5Chain: List<String>? = null,
    val issuerDid: String? = null,
    val credentialOffer: CredentialOffer? = null,
    val authorizationRequest: Map<String, List<String>>? = null,
    val externalAuthorizationState: String? = null,
    val authorizationClaims: JsonObject? = null,
    val expiresAt: Instant,
    val status: IssuanceSessionStatus = IssuanceSessionStatus.ACTIVE,
    val statusReason: String? = null,
    val issuedCredentialFormat: String? = null,
    val webhookUrl: String? = null,
)