package id.walt.commons.events

import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Serializable
class VerificationEvent(
  override val target: String,
  override val timestamp: Long,
  override val status: EventStatus,
  val sessionId: String,
  val format: String,
  val signatureAlgorithm: String,
  val credentialType: String? = null,
  val holderId: String? = null,
  override val callId: String? = null,
  override val error: String? = null
) : Event(Uuid.random().toHexString(), EventType.VerificationEvent)
