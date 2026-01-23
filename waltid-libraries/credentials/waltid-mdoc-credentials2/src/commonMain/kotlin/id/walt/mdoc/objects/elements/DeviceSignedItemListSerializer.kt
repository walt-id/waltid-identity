@file:OptIn(ExperimentalTime::class, ExperimentalSerializationApi::class)

package id.walt.mdoc.objects.elements

import id.walt.mdoc.credsdata.CredentialManager
import id.walt.mdoc.encoding.TransformingSerializerTemplate
import id.walt.mdoc.objects.MdocsCborSerializer
import kotlinx.datetime.LocalDate
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.mapSerialDescriptor
import kotlinx.serialization.encoding.*
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * A custom serializer that transforms a CBOR map of device-signed data into a `DeviceSignedItemList`.
 *
 * In the ISO/IEC 18013-5 specification, device-signed data for a given namespace is represented as a map:
 * `DeviceSignedItems = { + DataElementIdentifier => DataElementValue }`.
 *
 * This serializer allows the Kotlin code to work with a more object-oriented `List<DeviceSignedItem>`
 * by converting it to and from this underlying map structure during serialization. It relies on a
 * central `CborCredentialSerializer` to handle the type-specific serialization of each element's value.
 *
 * @see ISO/IEC 18013-5:xxxx(E), 8.3.2.1.2.3 (DeviceResponse CDDL for DeviceSignedItems)
 *
 * @param namespace The namespace of the data elements being serialized, which is required to look up
 * the correct value-specific serializers in `CborCredentialSerializer`.
 */
open class DeviceSignedItemListSerializer(private val namespace: String) :
    KSerializer<DeviceSignedItemList> {

    companion object {
        init {
            CredentialManager.init()
        }
    }

    override val descriptor: SerialDescriptor = mapSerialDescriptor(
        PrimitiveSerialDescriptor("key", PrimitiveKind.STRING),
        PrimitiveSerialDescriptor("value", PrimitiveKind.STRING)
    )

    override fun serialize(encoder: Encoder, value: DeviceSignedItemList) {
        encoder.encodeStructure(descriptor) {
            var index = 0
            value.entries.forEach {
                // Serialize the key (DataElementIdentifier)
                this.encodeStringElement(descriptor, index++, it.key)
                // Serialize the value
                this.encodeAnything(it, index++)
            }
        }
    }

    /**
     * Encodes a single `DeviceSignedItem`'s value based on its runtime type.
     *
     * Recommendation: This logic is complex. For better separation of concerns, the core
     * type-switching logic should ideally be fully encapsulated within `CborCredentialSerializer`.
     * This method would then become a simpler call to the central serializer.
     */
    private fun CompositeEncoder.encodeAnything(value: DeviceSignedItem, index: Int) {
        val elementValueSerializer = buildElementValueSerializer(namespace, value.value, value.key)
        val descriptor = mapSerialDescriptor(
            PrimitiveSerialDescriptor("key", PrimitiveKind.STRING),
            elementValueSerializer.descriptor
        )

        when (val it = value.value) {
            is String -> encodeStringElement(descriptor, index, it)
            is Int -> encodeIntElement(descriptor, index, it)
            is Long -> encodeLongElement(descriptor, index, it)
            is LocalDate -> encodeSerializableElement(descriptor, index, LocalDate.serializer(), it)
            is Instant -> encodeSerializableElement(descriptor, index, InstantStringSerializer, it)
            is Boolean -> encodeBooleanElement(descriptor, index, it)
            is ByteArray -> encodeSerializableElement(descriptor, index, ByteArraySerializer(), it)
            else -> MdocsCborSerializer.encode(namespace, value.key, descriptor, index, this, it)
        }
    }

    private inline fun <reified T> buildElementValueSerializer(
        namespace: String,
        elementValue: T,
        elementIdentifier: String
    ) = when (elementValue) {
        is String -> String.serializer()
        is Int -> Int.serializer()
        is Long -> Long.serializer()
        is LocalDate -> LocalDate.serializer()
        is Instant -> InstantStringSerializer
        is Boolean -> Boolean.serializer()
        is ByteArray -> ByteArraySerializer()
        is Any -> MdocsCborSerializer.lookupSerializer(namespace, elementIdentifier)
            ?: error("serializer not found for $elementIdentifier, with value $elementValue")

        else -> error("serializer not found for $elementIdentifier, with value $elementValue")
    }

    override fun deserialize(decoder: Decoder): DeviceSignedItemList {
        val entries = mutableListOf<DeviceSignedItem>()
        decoder.decodeStructure(descriptor) {
            lateinit var key: String
            var value: Any
            while (true) {
                val index = decodeElementIndex(descriptor)
                if (index == CompositeDecoder.DECODE_DONE) {
                    break
                } else if (index % 2 == 0) {
                    key = decodeStringElement(descriptor, index)
                } else if (index % 2 == 1) {
                    value = decodeAnything(index, key)
                    entries += DeviceSignedItem(key, value)
                }
            }
        }
        return DeviceSignedItemList(entries)
    }

    private fun CompositeDecoder.decodeAnything(index: Int, elementIdentifier: String?): Any {
        if (namespace.isBlank())
            println("This decoder is not namespace-aware! Unspeakable things may happen…")

        // Tags are not read out here but skipped because `decodeElementIndex` is never called, so we cannot
        // discriminate technically, this should be a good thing though, because otherwise we'd consume more from the
        // input
        elementIdentifier?.let {
            MdocsCborSerializer.decode(descriptor, index, this, elementIdentifier, namespace)
                ?.let { return it }
                ?: println(
                    "Falling back to defaults for namespace $namespace and elementIdentifier $elementIdentifier"
                )
        }

        // These are the ones that map to different CBOR data types, the rest don't, so if it is not registered, we'll
        // lose type information. No others must be added here, as they could consume data from the underlying bytes
        runCatching { return decodeStringElement(descriptor, index) }
        runCatching { return decodeLongElement(descriptor, index) }
        runCatching { return decodeDoubleElement(descriptor, index) }
        runCatching { return decodeBooleanElement(descriptor, index) }

        throw IllegalArgumentException("Could not decode value at $index")
    }
}


/** De-/serializes Instant */
internal object InstantStringSerializer : TransformingSerializerTemplate<Instant, String>(
    parent = String.serializer(),
    encodeAs = { it.toString() },
    decodeAs = { Instant.parse(it) }
)
