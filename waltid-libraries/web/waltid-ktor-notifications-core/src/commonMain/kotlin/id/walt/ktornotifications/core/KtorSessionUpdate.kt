package id.walt.ktornotifications.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class KtorSessionUpdate(
    val target: String,
    val event: String, // enum
    val session: JsonObject
)
