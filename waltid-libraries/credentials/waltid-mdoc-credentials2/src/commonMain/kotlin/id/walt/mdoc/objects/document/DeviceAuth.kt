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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Represents the device authentication data within a `DeviceSigned` structure.
 *
 * This structure provides proof of possession of the mdoc private key, which prevents cloning
 * and mitigates Man-in-the-Middle (MITM) attacks. It contains either a digital signature
 * (`deviceSignature`) or a message authentication code (`deviceMac`), but never both.
 *
 * The choice between a signature and a MAC has privacy implications; a MAC is not
 * non-repudiable, which can be preferable for the holder's privacy.
 *
 * @see ISO/IEC 18013-5:xxxx(E), 8.3.2.1.2.3 (DeviceResponse CDDL structure)
 * @see ISO/IEC 18013-5:xxxx(E), 9.1.3 (mdoc authentication mechanism)
 *
 * @property deviceSignature The COSE_Sign1 structure if authentication is performed via digital signature. Null otherwise.
 * @property deviceMac The COSE_Mac0 structure if authentication is performed via a Message Authentication Code. Null otherwise.
 */
@Serializable(with = DeviceAuthSerializer::class)
data class DeviceAuth(
    @SerialName("deviceSignature")
    val deviceSignature: CoseSign1? = null, // ByteArray
    @SerialName("deviceMac")
    val deviceMac: CoseMac0? = null, // ByteArray
    val extensions: Map<String, CborElement> = emptyMap(),
) {
    init {
        // Enforce the ISO/IEC 18013-5 rule that exactly one of the two fields must be present.
        require((deviceSignature == null) xor (deviceMac == null)) {
            "DeviceAuth must contain either a 'deviceSignature' or a 'deviceMac', but not both or neither."
        }
        requireNoExtensionCollisions(extensions, DEVICE_AUTH_FIELDS, "DeviceAuth")
    }
}

object DeviceAuthSerializer : KSerializer<DeviceAuth> {
    override val descriptor: SerialDescriptor = CborElement.serializer().descriptor

    override fun serialize(encoder: Encoder, value: DeviceAuth) {
        val fields = linkedMapOf<String, CborElement>()
        value.deviceSignature?.let {
            fields["deviceSignature"] = it.toCborElement(CoseSign1.serializer())
        }
        value.deviceMac?.let {
            fields["deviceMac"] = it.toCborElement(CoseMac0.serializer())
        }
        fields.putAll(value.extensions)
        encoder.encodeTextMap(fields)
    }

    override fun deserialize(decoder: Decoder): DeviceAuth {
        val fields = decoder.decodeTextMap("DeviceAuth")
        return DeviceAuth(
            deviceSignature = fields["deviceSignature"]?.fromCborElement(CoseSign1.serializer()),
            deviceMac = fields["deviceMac"]?.fromCborElement(CoseMac0.serializer()),
            extensions = fields.extensionsExcluding(DEVICE_AUTH_FIELDS),
        )
    }
}

private val DEVICE_AUTH_FIELDS = setOf("deviceSignature", "deviceMac")
