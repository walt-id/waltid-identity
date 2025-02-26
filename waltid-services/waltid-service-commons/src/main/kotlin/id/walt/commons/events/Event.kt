package id.walt.commons.events

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class  Event(
  @SerialName("_id") val id: String,
  val eventType: EventType,
  val userId: String? // authenticated user
) {
  abstract val target: String // organization.tenant
  abstract val timestamp: Long
  abstract val status: EventStatus
  abstract val action: String // e.g. received/accepted/rejected
  abstract val callId: String? // http tracing ID
  abstract val error: String?
}
