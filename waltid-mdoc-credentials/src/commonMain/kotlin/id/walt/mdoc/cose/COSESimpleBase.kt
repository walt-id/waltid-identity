package id.walt.mdoc.cose

import id.walt.mdoc.dataelement.*
import kotlinx.serialization.SerializationException

/**
 * Common base for COSESign1 and COSEMac0 data structures, with high level dynamic properties and methods
 */
abstract class COSESimpleBase<T: COSESimpleBase<T>>() {
  abstract val data: List<AnyDataElement>

  /**
   * Signed payload data
   */
  val payload: ByteArray?
    get() {
      if (data.size != 4) throw SerializationException("Invalid COSE_Sign1/COSE_Mac0 array")
      return when (data[2].type) {
        DEType.nil -> null
        DEType.byteString -> (data[2] as ByteStringElement).value
        else -> throw SerializationException("Invalid COSE_Sign1 payload")
      }
    }

  /**
   * Certificate chain, if present in unprotected header of COSE structure
   */
  val x5Chain: ByteArray?
    get() {
      if (data.size != 4) throw SerializationException("Invalid COSE_Sign1/COSE_Mac0 array")
      val unprotectedHeader = data[1] as? MapElement ?: throw SerializationException("Missing COSE_Sign1 unprotected header")
      return when (val headerParameter = unprotectedHeader.value[MapKey(X5_CHAIN)]) {
        is ByteStringElement -> headerParameter.value
        is ListElement -> {
          val byteArrays = headerParameter.value.map { (it as? ByteStringElement)?.value ?: ByteArray(0) }
          byteArrays.reduceOrNull { acc, bytes -> acc + bytes }
        }

        else -> null
      }
    }

  /**
   * Protected header data
   */
  val protectedHeader: ByteArray
    get() {
      if (data.size != 4) throw SerializationException("Invalid COSE_Sign1/COSE_Mac0 array")
      return (data[0] as ByteStringElement).value
    }

  /**
   * COSE Algorithm ID
   */
  val algorithm: Int
    get() = (DataElement.fromCBOR<MapElement>(protectedHeader).value[MapKey(ALG_LABEL)] as NumberElement).value.toInt()

  /**
   * COSE signature or tag (MAC) data
   */
  val signatureOrTag: ByteArray
    get() {
      if (data.size != 4) throw SerializationException("Invalid COSE_Sign1/COSE_Mac0 array")
      return (data[3] as ByteStringElement).value
    }

  protected fun replacePayload(payloadElement: AnyDataElement): List<AnyDataElement> {
    return data.mapIndexed { idx, el -> when(idx) {
      2 -> payloadElement
      else -> el
    } }
  }

  /**
   * Detach payload from COSE structure
   * @return COSE structure with payload set the NULL
   */
  abstract fun detachPayload(): T

  /**
   * Attach payload data to COSE structure
   * @param payload The payload data to set on the COSE structure
   * @return  COSE structure with payload set to given payload data
   */
  abstract fun attachPayload(payload: ByteArray): T

  /**
   * Convert to CBOR data element
   */
  fun toDE() = ListElement(data)
  /**
   * Serialize to CBOR data
   */
  fun toCBOR() = toDE().toCBOR()

  /**
   * Serialize to CBOR hex string
   */
  fun toCBORHex() = toDE().toCBORHex()
}