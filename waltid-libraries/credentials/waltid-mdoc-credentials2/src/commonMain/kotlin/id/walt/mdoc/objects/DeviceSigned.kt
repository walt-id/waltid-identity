@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.mdoc.objects

import id.walt.cose.CoseSign1
import id.walt.mdoc.encoding.ByteStringWrapper
import id.walt.mdoc.encoding.decodeTextMap
import id.walt.mdoc.encoding.encodeTextMap
import id.walt.mdoc.encoding.extensionsExcluding
import id.walt.mdoc.encoding.fromCborElement
import id.walt.mdoc.encoding.fromTaggedByteString
import id.walt.mdoc.encoding.requireNoExtensionCollisions
import id.walt.mdoc.encoding.toCborElement
import id.walt.mdoc.encoding.toTaggedByteString
import id.walt.mdoc.objects.document.DeviceAuth
import id.walt.mdoc.objects.elements.DeviceNameSpaces
import id.walt.mdoc.objects.elements.DeviceSignedItem
import id.walt.mdoc.objects.elements.DeviceSignedItemList
import kotlinx.serialization.KSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Represents the `DeviceSigned` structure within a `Document`. It contains data elements that are
 * attested to by the mdoc holder's device at the time of presentation, along with the cryptographic
 * proof of that attestation.
 *
 * @see ISO/IEC 18013-5:xxxx(E), 8.3.2.1.2.3 (Device retrieval mdoc response)
 *
 * @property namespaces A CBOR-tagged bytestring (`#6.24`) that, when decoded, contains a map of namespaces
 * to their respective device-signed data elements. This structure can be empty if no data elements
 * are returned, but the parent `DeviceSigned` structure must still be present.
 * @property deviceAuth The mandatory cryptographic proof (either a `COSE_Sign1` signature or a `COSE_Mac0` MAC)
 * that authenticates the mdoc and binds the entire transaction to the device's private key.
 */
@OptIn(ExperimentalUnsignedTypes::class, ExperimentalSerializationApi::class)
@Serializable(with = DeviceSignedSerializer::class)
data class DeviceSigned(
    @SerialName("nameSpaces")
    val namespaces: ByteStringWrapper<DeviceNameSpaces>,

    @SerialName("deviceAuth")
    val deviceAuth: DeviceAuth,
    val extensions: Map<String, CborElement> = emptyMap(),
) {
    init {
        requireNoExtensionCollisions(extensions, DEVICE_SIGNED_FIELDS, "DeviceSigned")
    }

    companion object {
        /**
         * A factory method to create a [DeviceSigned] instance from a structured map of items
         * and a [DeviceAuth] object.
         *
         * This is the preferred way to construct this object, as it correctly handles the nesting and
         * wrapping of the namespaces data.
         *
         * @param namespacedItems A map where the key is the namespace and the value is a list of
         * `DeviceSignedItem`s to be included.
         * @param deviceAuth The complete `DeviceAuth` object, which can be either a
         * `DeviceAuth.Signature` or a `DeviceAuth.Mac`.
         * @return A new, correctly structured [DeviceSigned] instance.
         */
        fun fromDeviceSignedItems(
            namespacedItems: Map<String, List<DeviceSignedItem>>,
            deviceAuth: CoseSign1, // ByteArray
            extensions: Map<String, CborElement> = emptyMap(),
        ): DeviceSigned = DeviceSigned(
            namespaces = ByteStringWrapper(DeviceNameSpaces(namespacedItems.map { (namespace, value) ->
                namespace to DeviceSignedItemList(value)
            }.toMap())),
            deviceAuth = DeviceAuth(deviceAuth),
            extensions = extensions,
        )
    }
}

object DeviceSignedSerializer : KSerializer<DeviceSigned> {
    override val descriptor: SerialDescriptor = CborElement.serializer().descriptor

    override fun serialize(encoder: Encoder, value: DeviceSigned) {
        val fields = linkedMapOf(
            "nameSpaces" to value.namespaces.toTaggedByteString(DeviceNameSpaces.serializer()),
            "deviceAuth" to value.deviceAuth.toCborElement(DeviceAuth.serializer()),
        )
        fields.putAll(value.extensions)
        encoder.encodeTextMap(fields)
    }

    override fun deserialize(decoder: Decoder): DeviceSigned {
        val fields = decoder.decodeTextMap("DeviceSigned")
        return DeviceSigned(
            namespaces = fields["nameSpaces"]?.fromTaggedByteString(
                DeviceNameSpaces.serializer(),
                "DeviceSigned nameSpaces",
            ) ?: throw SerializationException("DeviceSigned nameSpaces is required"),
            deviceAuth = fields["deviceAuth"]?.fromCborElement(DeviceAuth.serializer())
                ?: throw SerializationException("DeviceSigned deviceAuth is required"),
            extensions = fields.extensionsExcluding(DEVICE_SIGNED_FIELDS),
        )
    }
}

private val DEVICE_SIGNED_FIELDS = setOf("nameSpaces", "deviceAuth")
