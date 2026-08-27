@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.mdoc.objects.document

import id.walt.cose.CoseMac0
import id.walt.cose.CoseSign1
import id.walt.mdoc.encoding.decodeTextMap
import id.walt.mdoc.encoding.encodeTextMap
import id.walt.mdoc.encoding.extensionsExcluding
import id.walt.mdoc.encoding.fromCborElement
import id.walt.mdoc.encoding.requireNoExtensionCollisions
import id.walt.mdoc.encoding.toCborElement
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Represents the device authentication data within a `DeviceSigned` structure.
 *
 * ISO/IEC 18013-5 requires exactly one authentication method. The sealed variants make that
 * requirement structural: an application cannot construct both a signature and a MAC, or neither.
 *
 * @see ISO/IEC 18013-5, 10.3.2.1.2.3 and 12.4.4
 */
@Serializable(with = DeviceAuthSerializer::class)
sealed interface DeviceAuth {
    val extensions: Map<String, CborElement>

    /** Authentication by COSE_Sign1. */
    data class Signature(
        val signature: CoseSign1,
        override val extensions: Map<String, CborElement> = emptyMap(),
    ) : DeviceAuth {
        init {
            requireNoExtensionCollisions(extensions, DEVICE_AUTH_FIELDS, "DeviceAuth")
        }
    }

    /** Authentication by COSE_Mac0. */
    data class Mac(
        val mac: CoseMac0,
        override val extensions: Map<String, CborElement> = emptyMap(),
    ) : DeviceAuth {
        init {
            requireNoExtensionCollisions(extensions, DEVICE_AUTH_FIELDS, "DeviceAuth")
        }
    }
}

object DeviceAuthSerializer : KSerializer<DeviceAuth> {
    override val descriptor: SerialDescriptor = CborElement.serializer().descriptor

    override fun serialize(encoder: Encoder, value: DeviceAuth) {
        val fields = linkedMapOf<String, CborElement>()
        when (value) {
            is DeviceAuth.Signature -> fields["deviceSignature"] =
                value.signature.toCborElement(CoseSign1.serializer())
            is DeviceAuth.Mac -> fields["deviceMac"] = value.mac.toCborElement(CoseMac0.serializer())
        }
        fields.putAll(value.extensions)
        encoder.encodeTextMap(fields)
    }

    override fun deserialize(decoder: Decoder): DeviceAuth {
        val fields = decoder.decodeTextMap("DeviceAuth")
        val signature = fields["deviceSignature"]?.fromCborElement(CoseSign1.serializer())
        val mac = fields["deviceMac"]?.fromCborElement(CoseMac0.serializer())
        val extensions = fields.extensionsExcluding(DEVICE_AUTH_FIELDS)
        return when {
            signature != null && mac == null -> DeviceAuth.Signature(signature, extensions)
            signature == null && mac != null -> DeviceAuth.Mac(mac, extensions)
            else -> throw SerializationException(
                "DeviceAuth must contain exactly one of deviceSignature and deviceMac"
            )
        }
    }
}

private val DEVICE_AUTH_FIELDS = setOf("deviceSignature", "deviceMac")
