package id.walt.commons.audit

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class  AuditEvent(
  @SerialName("_id") val id: String,
  val eventType: EventType
) {
  abstract val target: String
  abstract val timestamp: Long
  abstract val status: EventStatus
  abstract val callId: String?
  abstract val error: String?
}
