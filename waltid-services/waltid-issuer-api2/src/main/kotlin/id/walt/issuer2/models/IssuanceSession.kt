package id.walt.issuer2.models

import id.walt.openid4vci.offers.CredentialOffer
import id.walt.openid4vci.offers.TxCode
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
enum class IssuanceSessionStatus {
    ACTIVE,
    CREDENTIAL_OFFER_RESOLVED,
    TOKEN_REQUESTED,
    CREDENTIAL_ISSUED,
    SUCCESSFUL,
    FAILED,
    EXPIRED,
}

@Serializable
data class IssuanceSessionRequest(
    val profileId: String,
    val credentialConfigurationId: String,
    val issuerKeyId: String,
    val issuerKey: JsonObject,
    val issuerDid: String? = null,
    val x5Chain: List<String>? = null,
    val credentialData: JsonObject,
    val mapping: JsonObject? = null,
    val selectiveDisclosureJson: JsonObject? = null,
)

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class IssuanceSession(
    val id: String = Uuid.random().toString(),
    val profileId: String,
    val credentialConfigurationId: String,
    val authMethod: AuthenticationMethod,
    var status: IssuanceSessionStatus = IssuanceSessionStatus.ACTIVE,
    val credentialOffer: CredentialOffer,
    val credentialOfferUri: String,
    val issuanceRequest: IssuanceSessionRequest,
    val txCode: TxCode? = null,
    val txCodeValue: String? = null,
    val createdAt: Instant = Clock.System.now(),
    val expiresAt: Instant = Clock.System.now().plus(5.minutes),
) {
    fun isExpired(): Boolean = Clock.System.now() > expiresAt
}

@Serializable
data class IssuanceSessionCreationResponse(
    val sessionId: String,
    val credentialOffer: String,
    val credentialOfferUri: String,
    val txCodeValue: String? = null,
    val expiresAt: Instant,
)

fun IssuanceSession.toCreationResponse() = IssuanceSessionCreationResponse(
    sessionId = id,
    credentialOffer = credentialOfferUri,
    credentialOfferUri = credentialOfferUri,
    txCodeValue = txCodeValue,
    expiresAt = expiresAt,
)
