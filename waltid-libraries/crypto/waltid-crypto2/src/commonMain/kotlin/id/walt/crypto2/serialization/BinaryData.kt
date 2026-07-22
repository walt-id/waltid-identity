package id.walt.crypto2.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.io.encoding.Base64

@Serializable(with = BinaryDataSerializer::class)
class BinaryData(bytes: ByteArray) {
    private val value = bytes.copyOf()

    val size: Int
        get() = value.size

    fun toByteArray(): ByteArray = value.copyOf()

    override fun equals(other: Any?): Boolean = other is BinaryData && value.contentEquals(other.value)

    override fun hashCode(): Int = value.contentHashCode()

    override fun toString(): String = "BinaryData(${value.size} bytes)"
}

object BinaryDataSerializer : KSerializer<BinaryData> {
    private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("id.walt.crypto2.serialization.BinaryData", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: BinaryData) {
        encoder.encodeString(base64Url.encode(value.toByteArray()))
    }

    override fun deserialize(decoder: Decoder): BinaryData = BinaryData(base64Url.decode(decoder.decodeString()))
}
