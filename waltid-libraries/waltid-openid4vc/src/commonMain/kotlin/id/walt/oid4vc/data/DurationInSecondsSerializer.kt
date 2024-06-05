package id.walt.oid4vc.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

object DurationInSecondsSerializer : KSerializer<Duration> {
    override val descriptor: SerialDescriptor
        get() = Duration.serializer().descriptor

    override fun deserialize(decoder: Decoder): Duration {
        return decoder.decodeLong().seconds
    }

    override fun serialize(encoder: Encoder, value: Duration) {
        encoder.encodeLong(value.inWholeSeconds)
    }

}
