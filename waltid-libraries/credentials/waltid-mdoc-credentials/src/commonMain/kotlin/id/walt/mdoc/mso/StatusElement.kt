package id.walt.mdoc.mso

import id.walt.mdoc.dataelement.*
import kotlinx.serialization.Serializable

@Serializable
class StatusElement (
  val statusListInfo: StatusListInfo
) {
  /**
   * Convert to CBOR map element
   */
  fun toMapElement() = mapOf(
    "status_list" to statusListInfo.toMapElement(),
  ).toDataElement()
}
