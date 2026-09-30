package id.walt.events

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The serial name is the class name from before the move out of service-commons: stored and exported events
// carry it as their type, and must stay readable.
@Serializable
@SerialName("id.walt.commons.events.IssuanceEvent")
class IssuanceEvent(
    override val originator: String?,
    override val organization: String,
    override val target: String,
    override val timestamp: Long,
    override val action: Action,
    override val status: Status,
    override val callId: String?,
    override val error: String?,

    val sessionId: String,
    val credentialConfigurationId: String,
    val format: String?,
    val proofType: String? = null,
    val holderId: String? = null
) : Event(EventType.IssuanceEvent) {
}
