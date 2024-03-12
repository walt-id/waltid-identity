package id.walt.issuer

import id.walt.credentials.vc.vcs.W3CVC
import id.walt.sdjwt.SDMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

sealed class BaseIssuanceRequest {
    abstract val issuanceKey: JsonObject
    abstract val issuerDid: String
    abstract val vc: W3CVC
    abstract val mapping: JsonObject?
}

@Serializable
data class JwtIssuanceRequest(
    override val issuanceKey: JsonObject, override val issuerDid: String,

    override val vc: W3CVC, override val mapping: JsonObject? = null
) : BaseIssuanceRequest()

@Serializable
data class SdJwtIssuanceRequest(
    override val issuanceKey: JsonObject,
    override val issuerDid: String,

    override val vc: W3CVC,
    override val mapping: JsonObject? = null,
    val selectiveDisclosure: SDMap? = null,
) : BaseIssuanceRequest()

@Serializable
data class IssuerOnboardingRequest(
    val issuanceKeyConfig: JsonObject, val issuerDidConfig: JsonObject
)

@Serializable
data class IssuerOnboardingResponse(
    val issuanceKey: JsonElement, val issuerDid: String
)
