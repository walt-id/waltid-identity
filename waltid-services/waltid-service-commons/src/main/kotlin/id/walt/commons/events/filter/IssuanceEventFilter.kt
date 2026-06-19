package id.walt.commons.events.filter

import kotlinx.serialization.Serializable

@Serializable
data class IssuanceEventFilter(
    val credentialConfigurationId: Set<String>? = null,
    val format: Set<String>? = null,
    val sessionId: String? = null,
    val proofType: Set<String>? = null,
    val holder: Set<String>? = null,
)
