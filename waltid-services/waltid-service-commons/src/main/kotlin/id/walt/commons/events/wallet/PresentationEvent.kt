package id.walt.commons.events.wallet

import id.walt.commons.events.*
import id.walt.commons.temp.UuidSerializer
import id.walt.oid4vc.data.ProofType
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Serializable
class PresentationEvent(
    override val originator: String?,
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
) : Event() {

}
