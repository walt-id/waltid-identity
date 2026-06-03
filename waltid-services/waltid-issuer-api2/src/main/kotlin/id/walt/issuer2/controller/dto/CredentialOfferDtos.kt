package id.walt.issuer2.controller.dto

import id.walt.mdoc.dataelement.json.JsonObjectToCborMappingConfig
import id.walt.openid4vci.offers.AuthenticationMethod
import id.walt.openid4vci.offers.CredentialOfferValueMode
import id.walt.openid4vci.offers.IssuerStateMode
import id.walt.openid4vci.offers.TxCode
import id.walt.sdjwt.SDMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.time.Duration.Companion.minutes

@Serializable
data class CredentialOfferRuntimeOverrides(
    val subjectId: String? = null,
    val issuerDid: String? = null,
    val credentialData: JsonObject? = null,
    val mapping: JsonObject? = null,
    val selectiveDisclosure: SDMap? = null,
    val idTokenClaimsMapping: Map<String, String>? = null,
    val mDocNameSpacesDataMappingConfig: Map<String, JsonObjectToCborMappingConfig>? = null,
    val x5Chain: List<String>? = null,
    val webhookUrl: String? = null,
)

@Serializable
data class CredentialOfferCreateRequest(
    val profileId: String,
    val authMethod: AuthenticationMethod,
    val issuerStateMode: IssuerStateMode = IssuerStateMode.OMIT,
    val valueMode: CredentialOfferValueMode = CredentialOfferValueMode.BY_REFERENCE,
    val expiresInSeconds: Long = 5.minutes.inWholeSeconds,
    val txCode: TxCode? = null,
    val txCodeValue: String? = null,
    val runtimeOverrides: CredentialOfferRuntimeOverrides? = null,
    val sessionId: String? = null,
) {
    init {
        require(profileId.isNotBlank()) { "profileId must not be blank" }
        require(expiresInSeconds == -1L || expiresInSeconds > 0) {
            "expiresInSeconds must be positive, or -1 for no expiry"
        }
        require(txCode == null || authMethod == AuthenticationMethod.PRE_AUTHORIZED) {
            "txCode is only supported for PRE_AUTHORIZED credential offers"
        }
        require(authMethod == AuthenticationMethod.PRE_AUTHORIZED || txCodeValue == null) {
            "txCodeValue is only supported for PRE_AUTHORIZED credential offers"
        }
        sessionId?.let { require(it.isNotBlank()) { "sessionId must not be blank" } }
    }
}

@Serializable
data class CredentialOfferCreateResponse(
    val offerId: String,
    val profileId: String,
    val authMethod: AuthenticationMethod,
    val issuerStateMode: IssuerStateMode,
    val expiresAt: Long,
    val txCodeValue: String? = null,
    val credentialOffer: String,
)
