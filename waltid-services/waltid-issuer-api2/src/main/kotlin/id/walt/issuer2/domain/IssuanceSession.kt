package id.walt.issuer2.domain

import id.walt.openid4vci.offers.CredentialOffer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.time.Instant

@Serializable
enum class AuthenticationMethod {
    PRE_AUTHORIZED,
    AUTHORIZATION_CODE,
}

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
    val profileVersion: Int,
    val authenticationMethod: AuthenticationMethod,
    val credentialConfigurationId: String,
    val credentialData: JsonObject,
    val mapping: JsonObject? = null,
    val idTokenClaimsMapping: Map<String, String>? = null,
    val mdocNamespacesDataMapping: Map<String, JsonObject>? = null,
    val issuerKeyId: String,
    val issuerDid: String? = null,
    val credentialOffer: CredentialOffer? = null,
    val authorizationRequest: Map<String, List<String>>? = null,
    val expiresAt: Instant,
    val status: IssuanceSessionStatus = IssuanceSessionStatus.ACTIVE,
    val statusReason: String? = null,
    val webhookUrl: String? = null,
)