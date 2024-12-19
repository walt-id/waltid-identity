package id.walt.mdoc.mso

import id.walt.mdoc.dataelement.*
import kotlinx.serialization.Serializable

@Serializable
class StatusListInfo (
  val idx: NumberElement,
  val uri: StringElement
) {

  constructor(idx: UInt, uri: String) : this(NumberElement(idx), StringElement(uri))

  /**
   * Convert to CBOR map element
   */
  fun toMapElement() = mapOf(
    "idx" to idx,
    "uri" to uri,
  ).toDataElement()
}
