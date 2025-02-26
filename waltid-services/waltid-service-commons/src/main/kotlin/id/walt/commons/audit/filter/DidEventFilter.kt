package id.walt.commons.audit.filter

import id.walt.commons.audit.DidEventType
import kotlinx.serialization.Serializable

@Serializable
data class DidEventFilter(
  val didEventType: Set<DidEventType>? = null,
  val didMethod: Set<String>? = null
)
