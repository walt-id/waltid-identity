package id.walt.commons.events

import kotlinx.serialization.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
data class Action(val type: String)
@Serializable
data class Status(val type: String)

@Serializable
sealed class Event @OptIn(ExperimentalUuidApi::class) constructor(
  val eventType: EventType, // event type to be stored in db and for easier filtering
  @SerialName("_id") val id: String = Uuid.random().toString()
) {
  abstract val originator: String? // user or system that initiated the event
  abstract val organization: String // organization.tenant
  abstract val target: String // organization.tenant
  abstract val timestamp: Long
  abstract val action: Action // e.g. received/accepted/rejected
  abstract val status: Status // e.g. success/failure
  abstract val callId: String? // http tracing ID
  abstract val error: String?
}


