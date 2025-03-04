package id.walt.commons.events

import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
@Serializable
class PresentationWalletEvent(
    override val originator: String?,
    override val organization: String,
    override val target: String,
    override val timestamp: Long,
    override val action: Action,
    override val status: Status,
    override val callId: String?,
    override val error: String?,

    // custom event data
    val tenant: String,
    val account: String,
    val presentationRequestUrl: String,
    val credentialId: String,
    val verifierId: String,
    val type: String
) : Event(EventType.PresentationWalletEvent) {
}
