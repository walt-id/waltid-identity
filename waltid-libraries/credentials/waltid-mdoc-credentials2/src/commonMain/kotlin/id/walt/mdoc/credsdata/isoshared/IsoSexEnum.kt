package id.walt.mdoc.credsdata.isoshared

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * ISO/IEC 5218 Codes for the representation of human sexes
 */
@Serializable(with = IsoSexEnumSerializer::class)
enum class IsoSexEnum(val code: Int) {

    NOT_KNOWN(0),
    MALE(1),
    FEMALE(2),
    NOT_APPLICABLE(9);

    companion object {
        fun parseCode(code: Int) = entries.firstOrNull { it.code == code } ?: throw IllegalArgumentException("Unknown sex: $code")
    }

}

object IsoSexEnumSerializer : KSerializer<IsoSexEnum> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("IsoSexEnum", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: IsoSexEnum) {
        value.let { encoder.encodeInt(it.code) }
    }

    override fun deserialize(decoder: Decoder): IsoSexEnum {
        return IsoSexEnum.parseCode(decoder.decodeInt())
    }

}
