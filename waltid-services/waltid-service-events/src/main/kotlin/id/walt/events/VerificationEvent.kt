package id.walt.events

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The serial name is the class name from before the move out of service-commons: stored and exported events
// carry it as their type, and must stay readable.
@Serializable
@SerialName("id.walt.commons.events.VerificationEvent")
class VerificationEvent(
    override val originator: String? = null,
    override val organization: String,
    override val target: String,
    override val timestamp: Long,
    override val action: Action,
    override val status: Status,
    override val callId: String? = null,
    override val error: String? = null,

    val sessionId: String,
    val format: String? = null,
    val signatureAlgorithm: String? = null,
    val credentialType: String? = null,
    val holderId: String? = null,
) : Event(EventType.VerificationEvent) {
}
