package id.walt.mdoc.objects.elements

import id.walt.cose.coseCompliantCbor
import id.walt.mdoc.encoding.ByteStringWrapper
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SealedSerializationApi
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.cbor.ValueTags
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.*
import net.orandja.obor.codec.Cbor
import net.orandja.obor.data.CborMap
import net.orandja.obor.data.CborObject
import net.orandja.obor.data.CborText

/**
 * Custom serializer for the `IssuerSignedList` class.
 *
 * This serializer handles the transformation between a Kotlin `List` of `IssuerSignedItem` objects
 * and the on-the-wire CBOR format, which is an array of tagged bytestrings.
 *
 * It is designed to work with a dynamic `IssuerSignedItemSerializer` for each item, which is
 * determined at runtime during deserialization.
 *
 * @see ISO/IEC 18013-5:xxxx(E), 8.3.2.1.2.3, which defines the `IssuerNameSpaces` structure as a map
 * where the value is `[+ IssuerSignedItemBytes]`. This serializer handles that array structure.
 *
 * @param namespace The namespace of the data elements being serialized/deserialized. This is crucial
 * for looking up the correct value-specific serializers in `CborCredentialSerializer`.
 */
@OptIn(ExperimentalSerializationApi::class)
open class IssuerSignedListSerializer(private val namespace: String) : KSerializer<IssuerSignedList> {

    /**
     * Manual implementation of the SerialDescriptor. It describes the serialized structure as a LIST.
     * The `getElementAnnotations` override is an attempt to specify that each element in the list
     * should have CBOR Tag 24, as required for `IssuerSignedItemBytes`.
     */
    @OptIn(SealedSerializationApi::class)
    @ExperimentalSerializationApi
    override val descriptor: SerialDescriptor = object : SerialDescriptor {
        override val elementsCount: Int = 1
        override val kind: SerialKind = StructureKind.LIST
        override val serialName: String = "kotlin.collections.ArrayList"
        override fun getElementName(index: Int): String = index.toString()
        override fun getElementIndex(name: String): Int = name.toInt()
        override fun isElementOptional(index: Int): Boolean = false

        // Defines the CBOR tag for each element in the list.
        @OptIn(ExperimentalUnsignedTypes::class)
        override fun getElementAnnotations(index: Int): List<Annotation> = listOf(ValueTags(24U))
        override fun getElementDescriptor(index: Int): SerialDescriptor = Byte.serializer().descriptor
    }


    override fun serialize(encoder: Encoder, value: IssuerSignedList) {
        encoder.encodeCollection(descriptor, value.entries.size) {
            value.entries.forEachIndexed { idx, it ->
                // NOTE: The specification requires each item in the list to be an `IssuerSignedItemBytes`,
                // which is defined as `#6.24(bstr.cbor IssuerSignedItem)`.
                // This implementation serializes the IssuerSignedItem to a byte array. The framework is
                // expected to apply the tag based on the descriptor's annotations.
                encodeSerializableElement(descriptor, idx, ByteArraySerializer(), it.value.serialize(namespace))
            }
        }
    }

    /**
     * Private helper to serialize an IssuerSignedItem to its CBOR byte representation using its dedicated serializer.
     */
    private fun IssuerSignedItem.serialize(namespace: String): ByteArray =
        coseCompliantCbor.encodeToByteArray(IssuerSignedItemSerializer(namespace, elementIdentifier), this)

    override fun deserialize(decoder: Decoder): IssuerSignedList {
        val entries = mutableListOf<ByteStringWrapper<IssuerSignedItem>>()
        decoder.decodeStructure(descriptor) {
            while (true) {
                val index = decodeElementIndex(descriptor)
                if (index == CompositeDecoder.DECODE_DONE) {
                    break
                }

                // 1. Decode the raw bytestring of one item from the list.
                val readBytes = decoder.decodeSerializableValue(ByteArraySerializer())

                // 2. Perform a partial, generic decoding to inspect the bytes and find the elementIdentifier.
                // This is necessary to select the correct type-specific serializer for the full decoding.
                val cborMap = Cbor.decodeFromByteArray<CborObject>(readBytes) as CborMap

                val elementIdItem = cborMap.first { (it.key as CborText).value == IssuerSignedItem.PROP_ELEMENT_ID }
                val elementId = (elementIdItem.value as CborText).value
                entries += ByteStringWrapper(
                    // 3. Perform the final, type-safe decoding using the specific serializer for that element.
                    coseCompliantCbor.decodeFromByteArray(
                        IssuerSignedItemSerializer(
                            namespace,
                            elementId
                        ), cborMap.cbor), // // Use the raw bytes of the map for final decoding
                    cborMap.cbor
                )
            }
        }
        return IssuerSignedList(entries)
    }
}
