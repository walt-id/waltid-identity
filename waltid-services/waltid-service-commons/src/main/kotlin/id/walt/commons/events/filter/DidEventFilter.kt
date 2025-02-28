package id.walt.commons.events.filter

import id.walt.commons.events.DidEventType
import kotlinx.serialization.Serializable

@Serializable
data class DidEventFilter(
  val didEventType: Set<DidEventType>? = null,
  val didMethod: Set<String>? = null
)
