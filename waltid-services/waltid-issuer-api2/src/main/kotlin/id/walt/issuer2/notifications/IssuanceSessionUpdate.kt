package id.walt.issuer2.notifications

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class IssuanceSessionUpdate(
    val id: String,
    val type: IssuanceSessionEvent,
    val data: JsonObject,
)
