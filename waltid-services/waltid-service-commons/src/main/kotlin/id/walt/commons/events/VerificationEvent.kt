package id.walt.commons.events

import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
@Serializable
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
