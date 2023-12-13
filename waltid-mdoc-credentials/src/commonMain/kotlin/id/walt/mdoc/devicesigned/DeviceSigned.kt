package id.walt.mdoc.devicesigned

import id.walt.mdoc.dataelement.EncodedCBORElement
import id.walt.mdoc.dataelement.MapElement
import id.walt.mdoc.dataelement.MapKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

/**
 * Data structure for device signed data
 * Contains device signed item namespaces as encoded CBOR data element, and device authentication
 */
@Serializable
data class DeviceSigned (
  val nameSpaces: EncodedCBORElement,
  val deviceAuth: DeviceAuth
) {
  /**
   * Convert to CBOR map element
   */
  fun toMapElement(): MapElement {
    return MapElement(buildMap {
      put(MapKey("nameSpaces"), nameSpaces)
      put(MapKey("deviceAuth"), deviceAuth.toMapElement())
    })
  }

  companion object {
    /**
     * Convert from CBOR map element
     */
    fun fromMapElement(mapElement: MapElement) = DeviceSigned(
      mapElement.value[MapKey("nameSpaces")] as? EncodedCBORElement ?: throw SerializationException("No nameSpaces property found on DeviceSigned object"),
      mapElement.value[MapKey("deviceAuth")]?.let { DeviceAuth.fromMapElement(it as MapElement) } ?: throw SerializationException("No deviceAuth property found on DeviceSigned object")
    )
  }
}