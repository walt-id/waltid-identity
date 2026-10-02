package id.walt.events.filter

import id.walt.events.KeyEventType
import kotlinx.serialization.Serializable

@Serializable
data class KeyEventFilter(
    val keyEventType: Set<KeyEventType>? = null,
    val keyAlgorithm: Set<String>? = null,
)
