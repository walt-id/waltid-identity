package id.walt.issuer

import id.walt.credentials.vc.vcs.W3CVC
import id.walt.sdjwt.SDMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

sealed class BaseIssuanceRequest {
    abstract val vc: W3CVC
    abstract val mapping: JsonObject?
}


@Serializable
class JwtIssuanceRequest(
    override val vc: W3CVC,
    override val mapping: JsonObject? = null
) : BaseIssuanceRequest() {
    companion object {
        /**
         * Return JwtIssuanceRequest of W3CVC in `vc` and mapping in `mapping` if it has `vc`. Otherwise,
         * return complete JSON as W3CVC and no mapping.
         */
        fun fromJsonObject(jsonObj: JsonObject): JwtIssuanceRequest {
            val maybeHasVc = jsonObj["vc"]?.jsonObject
            return when {
                maybeHasVc != null -> JwtIssuanceRequest(W3CVC(maybeHasVc), jsonObj["mapping"]?.jsonObject)
                else -> JwtIssuanceRequest(W3CVC(jsonObj), null)
            }
        }
    }
}

@Serializable
data class SdJwtIssuanceRequest(
    val issuanceKey: JsonObject,
    val issuerDid: String,

    override val vc: W3CVC,
    override val mapping: JsonObject? = null,
    val selectiveDisclosure: SDMap? = null,
) : BaseIssuanceRequest()
