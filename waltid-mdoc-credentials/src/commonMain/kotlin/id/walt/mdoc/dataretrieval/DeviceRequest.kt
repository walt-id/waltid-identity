package id.walt.mdoc.dataretrieval

import cbor.Cbor
import id.walt.mdoc.dataelement.*
import id.walt.mdoc.docrequest.MDocRequest
import kotlinx.serialization.*

/**
 * Device request structure containing MDoc requests
 */
@Serializable
class DeviceRequest(
  val docRequests: List<MDocRequest>,
  val version: StringElement = "1.0".toDE()
) {
  /**
   * Convert to CBOR map element
   */
  fun toMapElement() = mapOf(
    MapKey("version") to version,
    MapKey("docRequests") to ListElement(docRequests.map { it.toMapElement() })
  ).toDE()

  /**
   * Serialize to CBOR data
   */
  @OptIn(ExperimentalSerializationApi::class)
  fun toCBOR() = toMapElement().toCBOR()
  /**
   * Serialize to CBOR hex string
   */
  @OptIn(ExperimentalSerializationApi::class)
  fun toCBORHex() = toMapElement().toCBORHex()
  companion object {
    /**
     * Deserialize from CBOR data
     */
    @OptIn(ExperimentalSerializationApi::class)
    fun fromCBOR(cbor: ByteArray) = Cbor.decodeFromByteArray<DeviceRequest>(cbor)
    /**
     * Deserialize from CBOR hex string
     */
    @OptIn(ExperimentalSerializationApi::class)
    fun fromCBORHex(cbor: String) = Cbor.decodeFromHexString<DeviceRequest>(cbor)
  }
}