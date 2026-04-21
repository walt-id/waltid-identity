package id.walt.issuer2.models

import id.walt.ktornotifications.core.KtorSessionUpdate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

enum class IssuanceSessionEvent {
    OFFER_CREATED,
    OFFER_RESOLVED,
    TOKEN_REQUESTED,
    CREDENTIAL_ISSUED,
    SESSION_COMPLETED,
    SESSION_FAILED,
    SESSION_EXPIRED,
}

fun IssuanceSession.toSessionUpdate(event: IssuanceSessionEvent): KtorSessionUpdate {
    val sessionJson = Json.encodeToString(this)
    val sessionObject = Json.decodeFromString<JsonObject>(sessionJson)
    return KtorSessionUpdate(
        target = id,
        event = event.name,
        session = sessionObject
    )
}
