package id.walt.webwallet.config

import com.sksamuel.hoplite.Masked
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
data class PushConfig(
    val pushPublicKey: String,
    @Serializable(with = MaskedSerializer::class) val pushPrivateKey: Masked,
    val pushSubject: String
) : WalletConfig()

object MaskedSerializer : KSerializer<Masked> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("pushPrivateKey", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Masked) = encoder.encodeString(value.value)

    override fun deserialize(decoder: Decoder): Masked = Masked(decoder.decodeString())
}