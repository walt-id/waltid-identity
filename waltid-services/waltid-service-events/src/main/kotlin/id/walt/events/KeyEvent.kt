package id.walt.events

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The serial name is the class name from before the move out of service-commons: stored and exported events
// carry it as their type, and must stay readable.
@Serializable
@SerialName("id.walt.commons.events.KeyEvent")
@Deprecated("Not written by any service: key creation is a UsageEvent with usageType KEY_OPERATION. Kept so stored events stay readable.")
class KeyEvent(
    override val originator: String?,
    override val organization: String,
    override val target: String,
    override val timestamp: Long,
    override val action: Action,
    override val status: Status,
    override val callId: String?,
    override val error: String?,

    val keyEventType: KeyEventType,
    val keyAlgorithm: String
) : Event(EventType.KeyEvent) {
}
