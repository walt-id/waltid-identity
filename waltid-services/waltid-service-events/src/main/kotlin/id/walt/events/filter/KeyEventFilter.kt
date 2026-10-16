package id.walt.events.filter

import id.walt.events.KeyEventType
import kotlinx.serialization.Serializable

@Serializable
@Deprecated("KeyEvent is not written by any service; filter UsageEvent by usageType KEY_OPERATION instead.")
data class KeyEventFilter(
    val keyEventType: Set<KeyEventType>? = null,
    val keyAlgorithm: Set<String>? = null,
)
