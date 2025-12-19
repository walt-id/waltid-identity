@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.mdoc.encoding

import id.walt.cose.coseCompliantCbor
import id.walt.crypto.utils.Base64Utils.base64UrlDecode
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.cbor.CborDecoder
import kotlinx.serialization.cbor.CborEncoder
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.listSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

sealed class TemplateSerializer<T>(serialName: String = "") : KSerializer<T> {
    protected val realSerialName =
        serialName.ifEmpty {
            this::class.simpleName
                ?: throw IllegalArgumentException("Anonymous classes must specify a serialName explicitly")
        }
}

open class TransformingSerializerTemplate<ValueT, EncodedT>
    (
    private val parent: KSerializer<EncodedT>, private val encodeAs: (ValueT) -> EncodedT,
    private val decodeAs: (EncodedT) -> ValueT, serialName: String = ""
) : TemplateSerializer<ValueT>(serialName) {

    override val descriptor: SerialDescriptor =
        when (val kind = parent.descriptor.kind) {
            is PrimitiveKind -> PrimitiveSerialDescriptor(realSerialName, kind)
            else -> SerialDescriptor(realSerialName, parent.descriptor)
        }

    override fun serialize(encoder: Encoder, value: ValueT) {
        val v = try {
            encodeAs(value)
        } catch (x: Throwable) {
            throw SerializationException("Encoding failed", x)
        }
        encoder.encodeSerializableValue(parent, v)
    }

    override fun deserialize(decoder: Decoder): ValueT {
        val v = decoder.decodeSerializableValue(parent)
        try {
            return decodeAs(v)
        } catch (x: Throwable) {
            throw SerializationException("Decoding failed", x)
        }
    }
}

/** De-/serializes Base64Url strings to/from [ByteArray] */
object ByteArrayBase64UrlSerializer : TransformingSerializerTemplate<ByteArray, String>(
    parent = String.serializer(),
    encodeAs = { it.encodeToBase64Url() },
    decodeAs = { it.base64UrlDecode() }
)

sealed class ListSerializerTemplate<ValueT>(
    using: KSerializer<ValueT>, serialName: String = ""
) : TemplateSerializer<List<ValueT>>(serialName) {

    override val descriptor: SerialDescriptor =
        SerialDescriptor(realSerialName, listSerialDescriptor(using.descriptor))

    private val realSerializer = ListSerializer(using)
    override fun serialize(encoder: Encoder, value: List<ValueT>) =
        encoder.encodeSerializableValue(realSerializer, value)

    override fun deserialize(decoder: Decoder): List<ValueT> =
        decoder.decodeSerializableValue(realSerializer)

}

@Serializable(with = ByteStringWrapperSerializer::class)
class ByteStringWrapper<T>(
    val value: T,
    val serialized: ByteArray = byteArrayOf()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ByteStringWrapper<*>

        return value == other.value
    }

    override fun hashCode(): Int {
        return value?.hashCode() ?: 0
    }

    override fun toString(): String {
        return "ByteStringWrapper(value=$value, serialized=${serialized.contentToString()})"
    }
}

@OptIn(ExperimentalSerializationApi::class)
class ByteStringWrapperSerializer<T>(private val dataSerializer: KSerializer<T>) :
    KSerializer<ByteStringWrapper<T>> {

    override val descriptor: SerialDescriptor = dataSerializer.descriptor

    override fun serialize(encoder: Encoder, value: ByteStringWrapper<T>) {
        val bytes =
            (if (encoder is CborEncoder) encoder.cbor else coseCompliantCbor)
                .encodeToByteArray(dataSerializer, value.value)
        encoder.encodeSerializableValue(ByteArraySerializer(), bytes)
    }

    override fun deserialize(decoder: Decoder): ByteStringWrapper<T> {
        val bytes = decoder.decodeSerializableValue(ByteArraySerializer())
        val value = (if (decoder is CborDecoder) decoder.cbor else coseCompliantCbor)
            .decodeFromByteArray(dataSerializer, bytes)
        return ByteStringWrapper(value, bytes)
    }
}
