package id.walt.policies.policies.status.model

import id.walt.crypto.utils.Base64Utils.base64UrlDecode
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
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
    @Serializable(with = Base64UrlByteStringSerializer::class)
    override val list: String,
) : StatusContent()

/**
 * CBOR form of IETF Token Status List `lst` on [IETFStatusContent.list].
 *
 * JSON leaves the claim as a string. CBOR reads the byte string as base64url, and writes
 * base64url back to bytes, so the shared Token Status List codec can decode JWT and CWT lists
 * the same way.
 */
@OptIn(ExperimentalSerializationApi::class)
object Base64UrlByteStringSerializer : KSerializer<String> {
    override val descriptor = PrimitiveSerialDescriptor("Base64UrlByteString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        return if (decoder is CborDecoder) {
            decoder.decodeSerializableValue(ByteArraySerializer()).encodeToBase64Url()
        } else {
            decoder.decodeString()
        }
    }

    override fun serialize(encoder: Encoder, value: String) {
        if (encoder is CborEncoder) {
            encoder.encodeSerializableValue(ByteArraySerializer(), value.base64UrlDecode())
        } else {
            encoder.encodeString(value)
        }
    }
}