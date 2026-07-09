package id.walt.commons.events

import kotlinx.serialization.Serializable

@Serializable
class DidEvent(
    override val originator: String?,
    override val organization: String,
    override val target: String,
    override val timestamp: Long,
    override val action: Action,
    override val status: Status,
    override val callId: String?,
    override val error: String?,

    val didEventType: DidEventType,
    val didMethod: String,
) : Event(EventType.DIDEvent) {
}
