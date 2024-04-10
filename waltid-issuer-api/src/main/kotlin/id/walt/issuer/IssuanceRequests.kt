package id.walt.issuer

import id.walt.credentials.vc.vcs.W3CVC
import id.walt.did.dids.registrar.DidResult
import id.walt.sdjwt.SDMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

sealed class BaseIssuanceRequest {
    abstract val issuanceKey: JsonObject
    abstract val issuerDid: String
    abstract val credentialConfigurationId: String
    abstract val credentialData: W3CVC
    abstract val mapping: JsonObject?
    abstract val selectiveDisclosure: SDMap?
}

@Serializable
data class JwtIssuanceRequest(
    override val issuanceKey: JsonObject, override val issuerDid: String,

    override  val credentialConfigurationId: String, override val credentialData: W3CVC, override val mapping: JsonObject? = null
) : BaseIssuanceRequest() {
    override val selectiveDisclosure: SDMap?
        get() = null
}

@Serializable
data class SdJwtIssuanceRequest(
    override val issuanceKey: JsonObject,
    override val issuerDid: String,

    override  val credentialConfigurationId: String, override val credentialData: W3CVC,
    override val mapping: JsonObject? = null,
    override val selectiveDisclosure: SDMap? = null,
) : BaseIssuanceRequest()

@Serializable
data class IssuerOnboardingRequest(
    val issuerKeyConfig: JsonObject, val issuerDidConfig: JsonObject
)

@Serializable
data class IssuerOnboardingResponse(
    val issuerKey: JsonElement, val issuerDidDoc: DidResult
)
