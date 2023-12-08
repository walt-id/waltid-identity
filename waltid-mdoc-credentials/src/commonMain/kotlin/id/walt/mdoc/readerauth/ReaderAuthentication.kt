package id.walt.mdoc.readerauth

import cbor.Cbor
import id.walt.mdoc.dataelement.*
import id.walt.mdoc.mdocauth.DeviceAuthentication
import id.walt.mdoc.mdocauth.DeviceAuthenticationSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Reader authentication
 */
@Serializable(with = ReaderAuthenticationSerializer::class)
class ReaderAuthentication internal constructor(
  val data: List<AnyDataElement>
) {
  /**
   * Reader authentication object
   * @param sessionTranscript Session transcript of the ongoing reader session
   * @param itemsRequest CBOR encoded items requests
   */
  constructor(sessionTranscript: ListElement, itemsRequest: EncodedCBORElement) : this(
    listOf(
      StringElement("ReaderAuthentication"),
      sessionTranscript,
      itemsRequest
    )
  )

  /**
   * Serialize to CBOR data
   */
  @OptIn(ExperimentalSerializationApi::class)
  fun toCBOR() = Cbor.encodeToByteArray(ReaderAuthenticationSerializer, this)

  /**
   * Convert to CBOR data element
   */
  fun toDE() = ListElement(data)
}

@OptIn(ExperimentalSerializationApi::class)
@Serializer(forClass = ReaderAuthentication::class)
internal object ReaderAuthenticationSerializer: KSerializer<ReaderAuthentication> {
  override fun deserialize(decoder: Decoder): ReaderAuthentication {
    return ReaderAuthentication(
      decoder.decodeSerializableValue(ListSerializer(DataElementSerializer))
    )
  }

  override fun serialize(encoder: Encoder, value: ReaderAuthentication) {
    encoder.encodeSerializableValue(ListSerializer(DataElementSerializer), value.data)
  }
}