package id.walt.mdoc.cose

import id.walt.mdoc.dataelement.*
import korlibs.crypto.HMAC
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * COSE_Mac0 data structure, with built-in support for creating and verifying HMAC-256 macs
 */
@Serializable(with = COSEMac0Serializer::class)
class COSEMac0(
  override val data: List<AnyDataElement>
): COSESimpleBase<COSEMac0>() {
  constructor(): this(listOf())

  override fun detachPayload() = COSEMac0(replacePayload(NullElement()))
  override fun attachPayload(payload: ByteArray) = COSEMac0(replacePayload(ByteStringElement(payload)))

  /**
   * Verify a COSE_Mac0 using the given shared secret and optional external data
   * @param sharedSecret  The shared secret used for creating the MAC
   * @param externalData  Optional byte array with external application data
   * @return True if MAC has been verified
   */
  fun verify(sharedSecret: ByteArray, externalData: ByteArray = byteArrayOf()): Boolean {
    val mac0Content = createMacStructure(protectedHeader, payload ?: throw Exception("No payload given"), externalData).toCBOR()
    val tag = when(algorithm) {
      HMAC256 -> HMAC.hmacSHA256(sharedSecret, mac0Content).bytes
      else -> throw Exception("Algorithm $algorithm currently not supported, only supported algorithm is HMAC256 ($HMAC256)")
    }
    return signatureOrTag.contentEquals(tag)
  }

  companion object {
    private fun createMacStructure(protectedHeaderData: ByteArray, payload: ByteArray, externalData: ByteArray): ListElement {
      return ListElement(listOf(
        StringElement("MAC0"),
        ByteStringElement(protectedHeaderData),
        ByteStringElement(externalData),
        ByteStringElement(payload)
      ))
    }

    /**
     * Create COSE_Mac0 for the given payload, shared secret and optional external data
     * Only supports HMAC-256 for now
     * @param payload The payload for which the MAC is created
     * @param sharedSecret  Shared secret used for creating the MAC
     * @param externalData  Optional external application data to be added to the COSE_Mac0 structure before signing
     * @return  COSE_Mac0 structure containing the COSE headers, payload and MAC (tag)
     */
    fun createWithHMAC256(payload: ByteArray, sharedSecret: ByteArray, externalData: ByteArray = byteArrayOf()): COSEMac0 {
      val protectedHeaderData = mapOf(
          MapKey(ALG_LABEL) to NumberElement(HMAC256)
      ).toDE().toCBOR()

      val mac0Content = createMacStructure(protectedHeaderData, payload, externalData).toCBOR()
      val tag = HMAC.hmacSHA256(sharedSecret, mac0Content).bytes
      return COSEMac0(listOf(
        ByteStringElement(protectedHeaderData),
        MapElement(mapOf()),
        ByteStringElement(payload),
        ByteStringElement(tag)
      ))
    }
  }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializer(forClass = COSEMac0::class)
internal object COSEMac0Serializer {
  override fun serialize(encoder: Encoder, value: COSEMac0) {
    encoder.encodeSerializableValue(ListSerializer(DataElementSerializer), value.data)
  }

  override fun deserialize(decoder: Decoder): COSEMac0 {
    return COSEMac0(
      decoder.decodeSerializableValue(ListSerializer(DataElementSerializer))
    )
  }
}