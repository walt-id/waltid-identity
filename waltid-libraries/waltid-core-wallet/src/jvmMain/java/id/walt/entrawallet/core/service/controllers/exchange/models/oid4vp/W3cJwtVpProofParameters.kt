package id.walt.webwallet.web.controllers.exchange.models.oid4vp

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class W3cJwtVpProofParameters(
    val header: Map<String, JsonElement>,
    val payload: Map<String, JsonElement>,
)