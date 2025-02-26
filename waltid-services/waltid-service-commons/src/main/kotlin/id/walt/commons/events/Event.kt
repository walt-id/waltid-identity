package id.walt.commons.events

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
sealed class Action(val type: String)
@Serializable
sealed class Status(val type: String)

@Serializable
abstract class Event @OptIn(ExperimentalUuidApi::class) constructor(
  @SerialName("_id") val id: String = Uuid.random().toHexString(),
) {
  abstract val originator: String? // user or system that initiated the event
  abstract val target: String // organization.tenant
  abstract val timestamp: Long
  abstract val action: Action // e.g. received/accepted/rejected
  abstract val status: Status // e.g. success/failure
  abstract val callId: String? // http tracing ID
  abstract val error: String?
}
