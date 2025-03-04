package id.walt.commons.events.filter

import id.walt.oid4vc.data.CredentialFormat
import id.walt.oid4vc.data.ProofType
import kotlinx.serialization.Serializable

@Serializable
data class IssuanceEventFilter(
  val credentialConfigurationId: Set<String>? = null,
  val format: Set<CredentialFormat>? = null,
  val sessionId: String? = null,
  val proofType: Set<ProofType>? = null,
  val holder: Set<String>? = null,
)
