package id.walt.policies2.vc.policies.status.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.cbor.CborDecoder
import kotlinx.serialization.cbor.CborEncoder
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonClassDiscriminator

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("status")
sealed class StatusContent {
    abstract val list: String
}

@Serializable
@SerialName("W3CStatusContent")
data class W3CStatusContent(
    val type: String,
    @SerialName("statusPurpose")
    val purpose: String? = "revocation",
    @SerialName("encodedList")
    override val list: String,
) : StatusContent()

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@SerialName("IETFStatusContent")
data class IETFStatusContent(
    @SerialName("bits")
    val size: Int = 1,
    @SerialName("lst")
    @Serializable(with = StringOrByteStringSerializer::class)
    override val list: String,
) : StatusContent()

@OptIn(ExperimentalSerializationApi::class)
object StringOrByteStringSerializer : KSerializer<String> {
    override val descriptor = PrimitiveSerialDescriptor("StringOrByteString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        return if (decoder is CborDecoder) {
            decoder.decodeSerializableValue(ByteArraySerializer()).decodeToString()
        } else {
            decoder.decodeString()
        }
    }

    override fun serialize(encoder: Encoder, value: String) {
        if (encoder is CborEncoder) {
            encoder.encodeSerializableValue(ByteArraySerializer(), value.encodeToByteArray())
        } else {
            encoder.encodeString(value)
        }
    }
}