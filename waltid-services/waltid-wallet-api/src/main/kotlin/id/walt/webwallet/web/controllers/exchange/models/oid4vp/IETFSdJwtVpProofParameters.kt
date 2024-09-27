package id.walt.webwallet.web.controllers.exchange.models.oid4vp

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class IETFSdJwtVpProofParameters(
    val credentialId: String,
    val sdJwtVc: String,
    val header: Map<String, JsonElement>,
    val payload: Map<String, JsonElement>,
)