package id.walt.mdoc.objects.elements

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Serializer for the [DeviceNameSpaces] class.
 *
 * ## Rationale for this custom serializer:
 * The ISO specification for `DeviceNameSpaces` is a map where the key is a `namespace` string and the value
 * is another map of data elements (`DeviceSignedItems`). The serializer for the inner map (`DeviceSignedItemListSerializer`)
 * needs to know which `namespace` it is currently processing to correctly deserialize the `Any` typed values.
 *
 * Standard `kotlinx.serialization.MapSerializer` does not provide a mechanism to pass a map's key as context
 * to the value's serializer. This implementation is a workaround for that limitation. It uses a stateful
 * helper class (`NamespacedMapEntryDeserializer`) that captures the `namespace` key as a side effect
 * while the key is being deserialized, and then uses that captured key to initialize the real value serializer.
 *
 * @see ISO/IEC 18013-5:xxxx(E), 8.3.2.1.2.3 (DeviceResponse CDDL for DeviceNameSpaces)
 */
object NamespacedDeviceNameSpacesSerializer : KSerializer<DeviceNameSpaces> {

    /**
     * The descriptor is delegated to a standard MapSerializer, as the on-the-wire format is a map.
     */
    private val mapSerializer = MapSerializer(String.serializer(), object : DeviceSignedItemListSerializer("") {})

    override val descriptor = mapSerializer.descriptor

    override fun deserialize(decoder: Decoder): DeviceNameSpaces =
        DeviceNameSpaces(NamespacedMapEntryDeserializer().let {
            MapSerializer(it.namespaceSerializer, it.itemSerializer).deserialize(decoder)
        })

    /**
     * Internal, stateful helper class that manages the process of serializing/deserializing
     * a single key-value pair from the `DeviceNameSpaces` map.
     *
     * It holds the `namespaceKey` as state between the serialization of the key and the value.
     * This is an advanced and unconventional pattern, used here to solve a specific context-passing problem.
     */
    class NamespacedMapEntryDeserializer {
        /** This property holds the state (the namespace key) during a single map entry operation. */
        lateinit var key: String

        val namespaceSerializer = NamespaceSerializer()
        val itemSerializer = DeviceSignedItemListSerializer()

        /**
         * A custom serializer for the map KEY (`namespace`).
         * Its only purpose is to capture the namespace string as a side effect into the `namespaceKey` property.
         */
        inner class NamespaceSerializer internal constructor() : KSerializer<String> {
            override val descriptor = PrimitiveSerialDescriptor("ISO namespace", PrimitiveKind.STRING)

            override fun deserialize(decoder: Decoder): String = decoder.decodeString().apply { key = this }

            override fun serialize(encoder: Encoder, value: String) {
                encoder.encodeString(value).also { key = value }
            }
        }

        /**
         * A custom serializer for the map VALUE (`DeviceSignedItemList`).
         * It acts as a proxy. It uses the `namespaceKey` captured by the [NamespaceSerializer]
         * to instantiate the *actual* [DeviceSignedItemListSerializer] with the required context.
         */
        inner class DeviceSignedItemListSerializer internal constructor() : KSerializer<DeviceSignedItemList> {
            override val descriptor = mapSerializer.descriptor

            override fun deserialize(decoder: Decoder): DeviceSignedItemList =
                decoder.decodeSerializableValue(DeviceSignedItemListSerializer(key))

            override fun serialize(encoder: Encoder, value: DeviceSignedItemList) {
                encoder.encodeSerializableValue(DeviceSignedItemListSerializer(key), value)
            }
        }
    }

    override fun serialize(encoder: Encoder, value: DeviceNameSpaces) =
        NamespacedMapEntryDeserializer().let {
            MapSerializer(it.namespaceSerializer, it.itemSerializer).serialize(encoder, value.entries)
        }
}
