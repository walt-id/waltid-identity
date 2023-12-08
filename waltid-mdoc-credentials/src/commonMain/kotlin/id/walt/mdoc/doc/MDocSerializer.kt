package id.walt.mdoc.doc

import id.walt.mdoc.dataelement.DataElementSerializer
import id.walt.mdoc.dataelement.MapElement
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@OptIn(ExperimentalSerializationApi::class)
@Serializer(forClass = MDoc::class)
internal object MDocSerializer : KSerializer<MDoc> {
  override fun serialize(encoder: Encoder, value: MDoc) {
    encoder.encodeSerializableValue(DataElementSerializer, value.toMapElement())
  }
  override fun deserialize(decoder: Decoder): MDoc {
    return MDoc.fromMapElement(decoder.decodeSerializableValue(DataElementSerializer) as MapElement)
  }
}