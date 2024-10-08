package id.walt.mdoc.dataretrieval

import cbor.Cbor
import id.walt.mdoc.dataelement.ListElement
import id.walt.mdoc.dataelement.MapKey
import id.walt.mdoc.dataelement.StringElement
import id.walt.mdoc.dataelement.toDataElement
import id.walt.mdoc.docrequest.MDocRequest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.decodeFromHexString

/**
 * Device request structure containing MDoc requests
 */
@Serializable
class DeviceRequest(
  val docRequests: List<MDocRequest>,
  val version: StringElement = "1.0".toDataElement()
) {
  /**
   * Convert to CBOR map element
   */
  fun toMapElement() = mapOf(
    MapKey("version") to version,
    MapKey("docRequests") to ListElement(docRequests.map { it.toMapElement() })
  ).toDataElement()

  /**
   * Serialize to CBOR data
   */
  fun toCBOR() = toMapElement().toCBOR()
  /**
   * Serialize to CBOR hex string
   */
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
