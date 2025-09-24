package id.walt.mdoc.objects.elements

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Serializer for the `nameSpaces` map within the [IssuerSigned] structure.
 *
 * ## Rationale for this Custom Serializer:
 * The ISO specification for `IssuerNameSpaces` is a map where the key is a `namespace` string and
 * the value is a list of issuer-signed items. The serializer for this list (`IssuerSignedListSerializer`)
 * needs to know which `namespace` it is currently processing to correctly deserialize the items within.
 *
 * Standard `kotlinx.serialization.MapSerializer` does not provide a way to pass a map's key as context
 * to the value's serializer. This implementation is a workaround for that limitation. It uses a stateful
 * helper class (`NamespacedMapEntryManager`) that captures the `namespace` key as a side effect
 * while the key is being deserialized, and then uses that captured key to initialize the real value serializer.
 *
 * @see ISO/IEC 18013-5:xxxx(E), 8.3.2.1.2.3 (Device retrieval mdoc response)
 */
object NamespacedIssuerSignedListSerializer : KSerializer<Map<String, IssuerSignedList>> {
    private val mapSerializer = MapSerializer(String.serializer(), object : IssuerSignedListSerializer("") {})

    /**
     * The descriptor is based on the underlying Map that this serializer handles.
     */
    override val descriptor = mapSerializer.descriptor

    override fun deserialize(decoder: Decoder): Map<String, IssuerSignedList> = NamespacedMapEntryDeserializer().let {
        MapSerializer(it.namespaceSerializer, it.itemSerializer).deserialize(decoder)
    }

    /**
     * An internal, stateful helper class that manages the process of serializing/deserializing
     * a single key-value pair from the `IssuerNameSpaces` map.
     */
    class NamespacedMapEntryDeserializer {
        lateinit var key: String

        val namespaceSerializer = NamespaceSerializer()
        val itemSerializer = IssuerSignedListSerializer()

        inner class NamespaceSerializer internal constructor() : KSerializer<String> {
            override val descriptor = PrimitiveSerialDescriptor("ISO namespace", PrimitiveKind.STRING)

            override fun deserialize(decoder: Decoder): String = decoder.decodeString().apply { key = this }

            override fun serialize(encoder: Encoder, value: String) {
                encoder.encodeString(value).also { key = value }
            }
        }

        /**
         * A custom serializer PROXY for the map VALUE (`IssuerSignedList`).
         * It uses the `namespaceKey` captured by the [NamespaceSerializer] to instantiate the
         * actual [IssuerSignedListSerializer] with the required context, and then delegates the operation.
         */
        inner class IssuerSignedListSerializer internal constructor() : KSerializer<IssuerSignedList> {
            override val descriptor = mapSerializer.descriptor

            override fun deserialize(decoder: Decoder): IssuerSignedList =
                decoder.decodeSerializableValue(IssuerSignedListSerializer(key))

            override fun serialize(encoder: Encoder, value: IssuerSignedList) {
                encoder.encodeSerializableValue(IssuerSignedListSerializer(key), value)
            }
        }
    }

    override fun serialize(encoder: Encoder, value: Map<String, IssuerSignedList>) =
        NamespacedMapEntryDeserializer().let {
            MapSerializer(it.namespaceSerializer, it.itemSerializer).serialize(encoder, value)
        }
}
