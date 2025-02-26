package id.walt.commons.audit

import id.walt.oid4vc.data.ProofType
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Serializable
class IssuanceEvent(
  override val target: String,
  override val timestamp: Long,
  override val status: EventStatus,
  val sessionId: String,
  val credentialConfigurationId: String,
  val format: String?,
  val proofType: ProofType? = null,
  val holderId: String? = null,
  override val callId: String? = null,
  override val error: String? = null
) : AuditEvent(Uuid.random().toHexString(), EventType.IssuanceEvent)
