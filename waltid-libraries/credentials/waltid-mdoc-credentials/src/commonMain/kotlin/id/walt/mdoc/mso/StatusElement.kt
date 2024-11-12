package id.walt.mdoc.mso

import id.walt.mdoc.dataelement.*
import kotlinx.serialization.Serializable

/**
 * Mobile security object, representing the payload of the issuer signature, for the issuer signed part of the mdoc.
 * @param version Version of the MSO
 * @param digestAlgorithm digest algorithm used
 * @param valueDigests  Digests of the signed items
 * @param deviceKeyInfo Device key info, containing the mdoc authentication public key and related information
 * @param docType Document type
 * @param validityInfo Time-wise validity information about this MSO
 */
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
