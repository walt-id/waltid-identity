package id.walt.events.filter

import id.walt.events.DeviceFlow
import kotlinx.serialization.Serializable

@Serializable
data class VerificationEventFilter(
    val format: Set<String>? = null,
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
