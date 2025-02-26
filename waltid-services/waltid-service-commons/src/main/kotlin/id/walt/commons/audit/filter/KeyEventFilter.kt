package id.walt.commons.audit.filter

import id.walt.commons.audit.KeyEventType
import kotlinx.serialization.Serializable

@Serializable
data class KeyEventFilter(
  val keyEventType: Set<KeyEventType>? = null,
  val keyAlgorithm: Set<String>? = null,
)
