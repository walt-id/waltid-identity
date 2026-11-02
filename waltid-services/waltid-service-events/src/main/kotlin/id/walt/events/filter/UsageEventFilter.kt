package id.walt.events.filter

import kotlinx.serialization.Serializable

@Serializable
data class UsageEventFilter(
    /** Usage types, e.g. `CREDENTIAL_RECEIVED`, `KEY_OPERATION`. */
    val usageType: Set<String>? = null,
)
