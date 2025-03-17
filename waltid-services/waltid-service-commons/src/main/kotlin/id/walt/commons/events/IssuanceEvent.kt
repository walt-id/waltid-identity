package id.walt.commons.events

import id.walt.oid4vc.data.ProofType
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
@Serializable
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
  val proofType: ProofType? = null,
  val holderId: String? = null
) : Event(EventType.IssuanceEvent) {
}
