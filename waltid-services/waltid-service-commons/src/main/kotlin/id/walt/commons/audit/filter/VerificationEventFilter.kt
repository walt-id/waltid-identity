package id.walt.commons.audit.filter

import id.walt.commons.audit.DeviceFlow
import id.walt.oid4vc.data.CredentialFormat
import id.walt.oid4vc.data.ProofType
import kotlinx.serialization.Serializable

@Serializable
data class VerificationEventFilter(
  val format: Set<CredentialFormat>? = null,
  val signatureAlgorithm: Set<String>? = null,
  val sessionId: String? = null,
  val holder: Set<String>? = null,
  val credentialType: Set<String>? = null,
  val ecosystem: Set<String>? = null,
  val walletId: Set<String>? = null,
  val protocol: Set<String>? = null,
  val deviceFlow: Set<DeviceFlow>? = null,
  val asyncFlow: Set<Boolean>? = null
)
