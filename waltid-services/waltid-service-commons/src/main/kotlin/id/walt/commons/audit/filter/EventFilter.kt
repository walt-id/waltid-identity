package id.walt.commons.audit.filter

import id.walt.commons.audit.EventStatus
import id.walt.commons.audit.EventType
import kotlinx.serialization.Serializable

@Serializable
data class EventFilter(
  val eventType: Set<EventType>? = null,
  val status: Set<EventStatus>? = null,
  val fromTimestamp: Long? = null,
  val toTimestamp: Long? = null,
  val callId: String? = null,
  val issuanceEventFilter: IssuanceEventFilter? = null,
  val verificationEventFilter: VerificationEventFilter? = null,
  val keyEventFilter: KeyEventFilter? = null,
  val didEventFilter: DidEventFilter? = null
)
