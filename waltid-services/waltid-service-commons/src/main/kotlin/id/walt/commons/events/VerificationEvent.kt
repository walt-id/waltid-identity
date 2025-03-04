package id.walt.commons.events

import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
@Serializable
class VerificationEvent(
  override val originator: String?,
  override val organization: String,
  override val target: String,
  override val timestamp: Long,
  override val action: Action,
  override val status: Status,
  override val callId: String?,
  override val error: String?,

  val sessionId: String,
  val format: String,
  val signatureAlgorithm: String,
  val credentialType: String? = null,
  val holderId: String? = null,
) : Event(EventType.VerificationEvent) {
}
