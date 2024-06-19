package id.walt.oid4vc.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = GrantTypeSerializer::class)
enum class GrantType(val value: String) {
    implicit("implicit"),
    authorization_code("authorization_code"),
    pre_authorized_code("urn:ietf:params:oauth:grant-type:pre-authorized_code");

    companion object {
        fun fromValue(value: String): GrantType? {
            return entries.find { it.value == value }
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializer(forClass = GrantType::class)
object GrantTypeSerializer : KSerializer<GrantType> {
    override fun serialize(encoder: Encoder, value: GrantType) {
        encoder.encodeString(value.value)
    }

    override fun deserialize(decoder: Decoder): GrantType {
        return GrantType.fromValue(decoder.decodeString())!!
    }
}

object GrantTypeSetSerializer : KSerializer<Set<GrantType>> {
    val internalSerializer = SetSerializer(GrantTypeSerializer)
    override val descriptor = internalSerializer.descriptor
    override fun deserialize(decoder: Decoder) = internalSerializer.deserialize(decoder)
    override fun serialize(encoder: Encoder, value: Set<GrantType>) = internalSerializer.serialize(encoder, value)
}

object GrantTypeDetailsMapSerializer : KSerializer<Map<GrantType, GrantDetails>> {
    val internalSerializer = MapSerializer(GrantTypeSerializer, GrantDetails.serializer())
    override val descriptor = internalSerializer.descriptor
    override fun deserialize(decoder: Decoder) = internalSerializer.deserialize(decoder)
    override fun serialize(encoder: Encoder, value: Map<GrantType, GrantDetails>) =
        internalSerializer.serialize(encoder, value)
}
