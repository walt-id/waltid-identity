package id.walt.mdoc.cose

import cbor.Cbor
import id.walt.mdoc.dataelement.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * COSE_Sign1 data structure
 */
@Serializable(with = COSESign1Serializer::class)
class COSESign1(
    override val data: List<AnyDataElement>
): COSESimpleBase<COSESign1>() {
    constructor(): this(listOf())

    override fun detachPayload() = COSESign1(replacePayload(NullElement()))
    override fun attachPayload(payload: ByteArray) = COSESign1(replacePayload(ByteStringElement(payload)))
}

@OptIn(ExperimentalSerializationApi::class)
@Serializer(forClass = COSESign1::class)
internal object COSESign1Serializer {
    override fun serialize(encoder: Encoder, value: COSESign1) {
        encoder.encodeSerializableValue(ListSerializer(DataElementSerializer), value.data)
    }

    override fun deserialize(decoder: Decoder): COSESign1 {
        return COSESign1(
            decoder.decodeSerializableValue(ListSerializer(DataElementSerializer))
        )
    }
}