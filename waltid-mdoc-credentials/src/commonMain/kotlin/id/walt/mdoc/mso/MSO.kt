package id.walt.mdoc.mso

import id.walt.mdoc.dataelement.*
import id.walt.mdoc.issuersigned.IssuerSignedItem
import kotlinx.serialization.ExperimentalSerializationApi
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
class MSO (
  val version: StringElement,
  val digestAlgorithm: StringElement,
  val valueDigests: MapElement,
  val deviceKeyInfo: DeviceKeyInfo,
  val docType: StringElement,
  val validityInfo: ValidityInfo
) {
  /**
   * Get map from digestID to digest value, for the items of the given name space
   * @param nameSpace Items name space
   * @return Key-value map of digest IDs and digest values
   */
  fun getValueDigestsFor(nameSpace: String): Map<Int, ByteArray> {
    val nameSpaceElement = valueDigests.value[MapKey(nameSpace)] ?: return mapOf()
    return (nameSpaceElement as MapElement).value.map { entry ->
      Pair(entry.key.int, (entry.value as ByteStringElement).value)
    }.toMap()
  }

  /**
   * Items name spaces
   */
  val nameSpaces
    get() = valueDigests.value.keys.map { it.str }

  /**
   * Convert to CBOR map element
   */
  fun toMapElement() = mapOf(
    "version" to version,
    "digestAlgorithm" to digestAlgorithm,
    "valueDigests" to valueDigests,
    "deviceKeyInfo" to deviceKeyInfo.toMapElement(),
    "docType" to docType,
    "validityInfo" to validityInfo.toMapElement()
  ).toDE()

  /**
   * Decode and verify the given items of the given name space
   * @param nameSpace The items name space
   * @param items the encoded items to verify
   * @return True if the items have been verified
   */
  fun verifySignedItems(nameSpace: String, items: List<EncodedCBORElement>): Boolean {
    val msoDigests = getValueDigestsFor(nameSpace)
    val algorithm = DigestAlgorithm.values().first { it.value == digestAlgorithm.value }
    return items.all {
      val digestId = it.decode<IssuerSignedItem>().digestID.value.toInt()
      return msoDigests.containsKey(digestId) && msoDigests[digestId]!!.contentEquals(digestItem(it, algorithm))
    }
  }

  companion object {
    /**
     * Create item digest, for the given item
     */
    fun digestItem(encodedItem: EncodedCBORElement, digestAlgorithm: DigestAlgorithm): ByteArray {
      return digestAlgorithm.getHasher().digest(encodedItem.toCBOR()).bytes
    }

    /**
     * Create MSO for the given items and parameters
     * @param nameSpaces Name spaces of issuer signed items, that should be protected by this MSO
     * @param deviceKeyInfo Device key info of the document holder
     * @param docType Document type
     * @param validityInfo Time-wise validity information of this MSO
     * @param digestAlgorithm Digest algorithm, defaults to SHA-256
     * @return The Mobile security object, protecting the given data
     */
    @OptIn(ExperimentalSerializationApi::class)
    fun createFor(nameSpaces: Map<String, List<IssuerSignedItem>>,
                  deviceKeyInfo: DeviceKeyInfo,
                  docType: String,
                  validityInfo: ValidityInfo,
                  digestAlgorithm: DigestAlgorithm = DigestAlgorithm.SHA256): MSO {
      return MSO(
        "1.0".toDE(),
        digestAlgorithm.value.toDE(),
        nameSpaces.mapValues { entry ->
          entry.value.map { item ->
            Pair(
              item.digestID.value.toInt(),
              ByteStringElement(
                digestItem(EncodedCBORElement(item.toMapElement()), digestAlgorithm)
              )
            )
          }.toMap().toDE()
        }.toDE(),
        deviceKeyInfo,
        docType.toDE(),
        validityInfo
      )
    }
  }
}