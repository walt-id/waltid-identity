package id.walt.issuer2.domain

import id.walt.mdoc.dataelement.json.JsonObjectToCborMappingConfig
import id.walt.issuer2.notifications.IssuanceNotifications
import id.walt.openid4vci.offers.AuthenticationMethod
import id.walt.openid4vci.offers.CredentialOffer
import id.walt.sdjwt.SDMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.time.Instant

@Serializable
enum class IssuanceSessionStatus {
    ACTIVE,
    SUCCESSFUL,
    UNSUCCESSFUL,
    REJECTED_BY_USER,
    EXPIRED,
}

@Serializable
data class IssuanceRequest(
    val credentialIdentifier: String,
    val profileId: String,
    val credentialConfigurationId: String,
    val issuerKey: JsonObject,
    /** Ordered proof keys pinned for an issuance retry; one configured key applies to every proof. */
    val expectedCredentialProofKeyJwks: List<JsonObject>? = null,
    val credentialData: JsonObject,
    val mapping: JsonObject? = null,
    val selectiveDisclosure: SDMap? = null,
    val idTokenClaimsMapping: Map<String, String>? = null,
    val mDocNameSpacesDataMappingConfig: Map<String, JsonObjectToCborMappingConfig>? = null,
    /** OpenID4VP transaction_data types the issued key may sign, embedded in the mdoc MSO. */
    val authorizedTransactionDataTypes: List<String>? = null,
    val x5Chain: List<String>? = null,
    val issuerDid: String? = null,
    val credentialStatus: JsonElement? = null,
    @Transient
    val crypto2IssuerStoredKey: String? = null,
)

@Serializable
data class IssuanceResult(
    val issuedAt: Instant,
    val issuedCredentialFormat: String,
)

@Serializable
data class IssuanceSession(
    val sessionId: String,
    val authenticationMethod: AuthenticationMethod,
    val issuanceRequests: List<IssuanceRequest>,
    val issuanceResults: Map<String, IssuanceResult> = emptyMap(),
    val credentialOffer: CredentialOffer? = null,
    val authorizationRequest: Map<String, List<String>>? = null,
    val externalAuthorizationState: String? = null,
    val authorizationClaims: JsonObject? = null,
    val expiresAt: Instant,
    val status: IssuanceSessionStatus = IssuanceSessionStatus.ACTIVE,
    val statusReason: String? = null,
    val notifications: IssuanceNotifications? = null,
    val isClosed: Boolean = false,
    val failure: IssuanceSessionFailure? = null,
) {
    init {
        require(issuanceRequests.isNotEmpty()) { "issuanceRequests must not be empty" }
        require(issuanceRequests.map { it.credentialIdentifier }.distinct().size == issuanceRequests.size) {
            "credentialIdentifier values must be unique within an issuance session"
        }
        require(issuanceResults.keys.all { resultId ->
            issuanceRequests.any { it.credentialIdentifier == resultId }
        }) { "issuanceResults must reference issuanceRequests in the same session" }
    }
}
