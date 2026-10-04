package id.walt.events.filter

import id.walt.events.DidEventType
import kotlinx.serialization.Serializable

@Serializable
data class DidEventFilter(
    val didEventType: Set<DidEventType>? = null,
    val didMethod: Set<String>? = null
)
