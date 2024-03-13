package id.walt.issuer

import id.walt.credentials.vc.vcs.W3CVC
import id.walt.sdjwt.SDMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class IssuanceRequest(
    val issuanceKey: JsonObject,
    val issuerDid: String,

    val credentialConfigurationId: String,
    val credentialData: W3CVC,
    val mapping: JsonObject? = null,
    val selectiveDisclosure: SDMap? = null,
)

@Serializable
data class IssuerOnboardingRequest(
    val issuanceKeyConfig: JsonObject, val issuerDidConfig: JsonObject
)

@Serializable
data class IssuerOnboardingResponse(
    val issuanceKey: JsonElement, val issuerDid: String
)
