package id.walt.commons.events.filter

import id.walt.commons.events.KeyEventType
import kotlinx.serialization.Serializable

@Serializable
data class KeyEventFilter(
  val keyEventType: Set<KeyEventType>? = null,
  val keyAlgorithm: Set<String>? = null,
)
