package id.walt.commons.events.wallet

import id.walt.commons.events.Event
import id.walt.commons.events.EventStatus
import id.walt.commons.events.EventType
import id.walt.oid4vc.data.ProofType
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class IssuanceEvent(
    override val target: String,
    override val timestamp: Long,
    override val status: EventStatus,
    val credentialOfferUrl: String,
    val credentialConfigurationId: String,
    val issuerId: String,
    val type: String,
    override val action: String,
    override val callId: String? = null,
    override val error: String? = null
) : Event(Uuid.random().toHexString(), EventType.IssuanceEvent, null)
