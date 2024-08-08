package id.walt.oid4vc.data

import kotlinx.serialization.*
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
data class TxCode(
    @SerialName("input_mode") val inputMode: TxInputMode? = TxInputMode.numeric,
    @SerialName("length") val length: Int? = null,
    @SerialName("description") val description: String? = null,
) {
    companion object {
        private val NUMERICS = '0'..'9'

        fun makeFor(pin: String, description: String): TxCode {
            val inputMode = if (pin.all { it in NUMERICS }) TxInputMode.numeric else TxInputMode.text

            return TxCode(inputMode, pin.length, description)
        }
    }
}

@Serializable(TxInputModeSerializer::class)
enum class TxInputMode(val value: String) {
    numeric("numeric"),
    text("text");

    companion object {
        fun fromValue(value: String): TxInputMode? {
            return entries.find { it.value == value }
        }

        private val NUMERICS = '0'..'9'

        fun forPin(pin: String?) = pin?.let { p -> if (p.all { it in NUMERICS }) numeric else text }
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializer(TxInputMode::class)
object TxInputModeSerializer : KSerializer<TxInputMode> {
    override fun serialize(encoder: Encoder, value: TxInputMode) {
        encoder.encodeString(value.value)
    }

    override fun deserialize(decoder: Decoder): TxInputMode {
        return TxInputMode.fromValue(decoder.decodeString())!!
    }
}
