package id.walt.events

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A counted operation, e.g. a credential received or presented by a wallet, a key created, a DID resolved:
 * [usageType] (the Enterprise Stack's usage type) done [amount] times for [target]. Written from the same record
 * that counts the operation for usage and licensing, so events and usage cannot disagree.
 */
@Serializable
@SerialName("id.walt.events.UsageEvent")
class UsageEvent(
    override val originator: String?,
    override val organization: String,
    override val target: String,
    override val timestamp: Long,
    override val action: Action,
    override val status: Status,
    override val callId: String?,
    override val error: String?,

    val usageType: String,
    val amount: Long,
    /** What else is known about the operation, e.g. the key algorithm or the DID method. */
    val details: Map<String, String> = emptyMap(),
) : Event(EventType.UsageEvent)
