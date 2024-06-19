package id.walt.mdoc.mso

import id.walt.mdoc.dataelement.MapElement
import id.walt.mdoc.dataelement.toDE
import kotlinx.serialization.Serializable

/**
 * Public key and related information, for key used for mdoc authentication
 * @param deviceKey Public part of key pair used for mdoc authentication
 * @param keyAuthorizations Elements that may be signed by this key, either full name spaces or per data element
 * @param keyInfo May contain extra info about this key
 */
@Serializable
class DeviceKeyInfo (
  val deviceKey: MapElement,
  val keyAuthorizations: MapElement? = null,
  val keyInfo: MapElement? = null
) {
  /**
   * Convert to CBOR map element
   */
  fun toMapElement() = buildMap {
    put("deviceKey", deviceKey)
    keyAuthorizations?.let { put("keyAuthorizations", it) }
    keyInfo?.let { put("keyInfo", it) }
  }.toDE()
}