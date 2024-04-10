package id.walt.issuer

import id.walt.credentials.vc.vcs.W3CVC
import id.walt.did.dids.registrar.DidResult
import id.walt.sdjwt.SDMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

sealed class BaseIssuanceRequest {
    abstract val issuerKey: JsonObject
    abstract val issuerDid: String
    abstract val vc: W3CVC
    abstract val mapping: JsonObject?
}

@Serializable
data class JwtIssuanceRequest(
    override val issuerKey: JsonObject, override val issuerDid: String,

    override val vc: W3CVC, override val mapping: JsonObject? = null
) : BaseIssuanceRequest()

@Serializable
data class SdJwtIssuanceRequest(
    override val issuerKey: JsonObject,
    override val issuerDid: String,

    override val vc: W3CVC,
    override val mapping: JsonObject? = null,
    val selectiveDisclosure: SDMap? = null,
) : BaseIssuanceRequest()

@Serializable
data class IssuerOnboardingRequest(
    val issuerKeyConfig: JsonObject, val issuerDidConfig: JsonObject
)

@Serializable
data class IssuerOnboardingResponse(
    val issuerKey: JsonElement, val issuerDidDoc: DidResult
)
