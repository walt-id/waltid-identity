package id.walt.commons.audit

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Serializable
class DidEvent(
  override val target: String,
  override val timestamp: Long,
  override val status: EventStatus,
  val didEventType: DidEventType,
  val didMethod: String,
  override val callId: String? = null,
  override val error: String? = null
) : AuditEvent(Uuid.random().toHexString(), EventType.DIDEvent)
