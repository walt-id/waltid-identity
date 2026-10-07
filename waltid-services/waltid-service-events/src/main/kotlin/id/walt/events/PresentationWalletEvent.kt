package id.walt.events

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The serial name is the class name from before the move out of service-commons: stored and exported events
// carry it as their type, and must stay readable.
@Serializable
@SerialName("id.walt.commons.events.PresentationWalletEvent")
@Deprecated("Not written by any service: a presentation by a wallet is a UsageEvent with usageType CREDENTIAL_PRESENTED. Kept so stored events stay readable.")
class PresentationWalletEvent(
    override val originator: String? = null,
    override val organization: String,
    override val target: String,
    override val timestamp: Long,
    override val action: Action,
    override val status: Status,
    override val callId: String? = null,
    override val error: String? = null,

    // custom event data
    val tenant: String,
    val account: String,
    val presentationRequestUrl: String,
    val credentialId: String,
    val verifierId: String,
    val type: String
) : Event(EventType.PresentationWalletEvent) {
}
