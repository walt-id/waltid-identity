package id.walt.webwallet.service.events

import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

data class Event(
    val event: String,
    val action: String,
    val timestamp: String = Clock.System.now().toString(),
    val tenant: String,
    val originator: String,
    val data: JsonObject,
) {
    constructor(action: EventType.Action, tenant: String, originator: String, data: EventData) : this(
        event = action.type,
        action = action.toString(),
        tenant = tenant,
        originator = originator,
        data = Json.encodeToJsonElement(data).jsonObject
    )
}