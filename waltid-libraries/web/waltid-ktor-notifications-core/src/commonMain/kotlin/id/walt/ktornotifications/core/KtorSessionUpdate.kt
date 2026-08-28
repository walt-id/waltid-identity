package id.walt.ktornotifications.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class KtorSessionUpdate(
    val target: String,
    val event: String, // enum
    val session: JsonObject,
    val requestId: String? = null,
    val error: String? = null,
    @SerialName("error_description")
    val errorDescription: String? = null,
) {
    constructor(target: String, event: String, session: JsonObject) :
            this(target, event, session, null, null, null)
}
