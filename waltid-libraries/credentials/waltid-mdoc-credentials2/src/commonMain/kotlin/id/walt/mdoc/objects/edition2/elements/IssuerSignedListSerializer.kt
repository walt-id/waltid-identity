package id.walt.mdoc.objects.edition2.elements

import id.walt.cose.coseCompliantCbor
import id.walt.mdoc.encoding.ByteStringWrapper
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SealedSerializationApi
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.cbor.ValueTags
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.*

/** Preserves each original tagged `IssuerSignedItemBytes` value in the namespace array. */
@OptIn(ExperimentalSerializationApi::class)
object IssuerSignedListSerializer : KSerializer<IssuerSignedList> {

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
                // Received items reuse their original bytes; newly created items are serialized here.
                // The framework applies the tag based on the descriptor's annotations.
                val serialized = it.serialized.takeIf { bytes -> bytes.isNotEmpty() }
                    ?: coseCompliantCbor.encodeToByteArray(IssuerSignedItem.serializer(), it.value)
                encodeSerializableElement(descriptor, idx, ByteArraySerializer(), serialized)
            }
        }
    }

    override fun deserialize(decoder: Decoder): IssuerSignedList {
        val entries = mutableListOf<ByteStringWrapper<IssuerSignedItem>>()
        decoder.decodeStructure(descriptor) {
            while (true) {
                val index = decodeElementIndex(descriptor)
                if (index == CompositeDecoder.DECODE_DONE) {
                    break
                }

                val readBytes = decoder.decodeSerializableValue(ByteArraySerializer())
                val item = coseCompliantCbor.decodeFromByteArray(IssuerSignedItem.serializer(), readBytes)
                entries += ByteStringWrapper(item, readBytes)
            }
        }
        return IssuerSignedList(entries)
    }
}
