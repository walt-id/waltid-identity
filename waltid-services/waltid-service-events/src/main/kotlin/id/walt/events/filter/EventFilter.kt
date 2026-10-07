package id.walt.events.filter

import id.walt.events.EventType
import kotlinx.serialization.Serializable

@Serializable
data class EventFilter(
    val eventType: Set<EventType>? = null,
    val status: Set<String>? = null,
    val action: Set<String>? = null,
    val fromTimestamp: Long? = null,
    val toTimestamp: Long? = null,
    val callId: String? = null,
    val target: Set<String>? = null,
    val issuanceEventFilter: IssuanceEventFilter? = null,
    val verificationEventFilter: VerificationEventFilter? = null,
    val keyEventFilter: KeyEventFilter? = null,
    val didEventFilter: DidEventFilter? = null,
    val usageEventFilter: UsageEventFilter? = null,
)
