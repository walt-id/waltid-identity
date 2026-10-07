package id.walt.events

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The serial name is the class name from before the move out of service-commons: stored and exported events
// carry it as their type, and must stay readable.
@Serializable
@SerialName("id.walt.commons.events.CredentialWalletEvent")
@Deprecated("Not written by any service: wallet activity is a UsageEvent (CREDENTIAL_RECEIVED, CREDENTIAL_PRESENTED, WALLET_CREATED). Kept so stored events stay readable.")
class CredentialWalletEvent(
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
    val credentialId: String,
    val credentialStatus: String
) : Event(EventType.CredentialWalletEvent) {
}
