package id.walt.events.filter

import id.walt.events.DidEventType
import kotlinx.serialization.Serializable

@Serializable
@Deprecated("DidEvent is not written by any service; filter UsageEvent by usageType DID_RESOLUTION instead.")
data class DidEventFilter(
    val didEventType: Set<DidEventType>? = null,
    val didMethod: Set<String>? = null
)
