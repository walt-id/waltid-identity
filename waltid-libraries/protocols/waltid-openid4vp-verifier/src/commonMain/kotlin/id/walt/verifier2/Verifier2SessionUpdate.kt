package id.walt.verifier2

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import ktornotifications.KtorSessionUpdate

@Serializable
data class Verifier2SessionUpdate(
    val target: String,
    val event: SessionEvent,
    val session: Verification2Session
) {
    fun toKtorSessionUpdate() =
        KtorSessionUpdate(target = target, event = event.toString(), session = Json.encodeToJsonElement(session).jsonObject)
}
