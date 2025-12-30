package id.walt.mdoc.objects.digest

import id.walt.cose.coseCompliantCbor
import id.walt.mdoc.crypto.MdocCrypto.digest
import id.walt.mdoc.objects.elements.IssuerSignedItem
import id.walt.mdoc.objects.elements.IssuerSignedItemSerializer
import id.walt.mdoc.objects.wrapInCborTag
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

/**
 * Represents a single key-value pair from the `DigestIDs` map within the Mobile Security Object (MSO).
 * The `DigestIDs` map associates a unique numeric `DigestID` with the hash of an `IssuerSignedItem`.
 *
 * @see ISO/IEC 18013-5:2021, 9.1.2.4 (MobileSecurityObject CDDL)
 *
 * @property key The `DigestID` (e.g., 0, 1, 2...), which is a unique unsigned integer within its namespace.
 * @property value The binary digest (hash) of the corresponding `IssuerSignedItemBytes`.
 */
data class ValueDigest(
    val key: UInt,
    val value: ByteArray,
) {

    /**
     * Note: `equals` and `hashCode` are manually overridden because the default implementation for a
     * `data class` uses reference equality for `ByteArray` properties. This override ensures
     * content-based equality for the digest value.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ValueDigest

        if (key != other.key) return false
        return value.contentEquals(other.value)
    }

    override fun hashCode(): Int {
        var result = key.hashCode()
        result = 31 * result + value.contentHashCode()
        return result
    }

    @OptIn(ExperimentalSerializationApi::class)
    companion object {
        /**
         * Factory method to create a [ValueDigest] by calculating the hash of an [IssuerSignedItem].
         * The process follows the specification for creating digests for the MSO.
         *
         * @see ISO/IEC 18013-5:2021, 9.1.2.5 (Message digest function)
         *
         * @param item The issuer-signed item to digest.
         * @param digestAlgorithm The algorithm specified in the MSO (e.g., "SHA-256").
         * @return A [ValueDigest] containing the item's `digestId` and its calculated hash.
         */
        fun fromIssuerSignedItem(item: IssuerSignedItem, namespace: String, digestAlgorithm: String): ValueDigest {
            // 1. Serialize the IssuerSignedItem to its canonical CBOR representation.
            val cborDataItem = coseCompliantCbor
                .encodeToByteArray(ByteArraySerializer(), item.serialize(namespace))

            // 2. Wrap the CBOR data in tag #24, as required for IssuerSignedItemBytes.
            val taggedBytes = cborDataItem.wrapInCborTag(24)

            // 3. Compute the digest using the specified algorithm.
            val digestValue = taggedBytes.digest(digestAlgorithm)

            return ValueDigest(item.digestId, digestValue)
        }

        private fun IssuerSignedItem.serialize(namespace: String): ByteArray =
            coseCompliantCbor.encodeToByteArray(IssuerSignedItemSerializer(namespace, elementIdentifier), this)
    }
}

/**
 * Represents the `DigestIDs` map from the MSO as a list of [ValueDigest] objects.
 *
 * The on-the-wire format in CBOR is a map (`DigestID => Digest`), but this class provides a more
 * object-oriented `List<ValueDigest>` API in Kotlin. The transformation is handled by the
 * custom [CborValueDigestListSerializer].
 *
 * @see ISO/IEC 18013-5:2021, 9.1.2.4
 *
 * @property entries The list of digest entries.
 */
@Serializable(with = ValueDigestList.CborValueDigestListSerializer::class)
data class ValueDigestList(
    val entries: List<ValueDigest>
) {
    /**
     * Serializes a `List<ValueDigest>` to and from a CBOR map structure.
     * This allows the Kotlin code to use a list of objects while adhering to the
     * map-based format defined in the ISO standard.
     */
    object CborValueDigestListSerializer : KSerializer<ValueDigestList> {

        @OptIn(ExperimentalSerializationApi::class)
        override val descriptor: SerialDescriptor = mapSerialDescriptor(
            //JsonBsonEncoder doesn't like the int value as map key
            //StreamingJsonEncoder seems to ignore this PrimitiveKind.INT and writes map key as String
            keyDescriptor = PrimitiveSerialDescriptor("key", PrimitiveKind.INT),
            valueDescriptor = listSerialDescriptor<Byte>(),
        )

        override fun serialize(encoder: Encoder, value: ValueDigestList) {
            encoder.encodeStructure(descriptor) {
                var index = 0
                value.entries.forEach {
                    this.encodeIntElement(descriptor, index++, it.key.toInt())
                    this.encodeSerializableElement(descriptor, index++, ByteArraySerializer(), it.value)
                }
            }
        }

        override fun deserialize(decoder: Decoder): ValueDigestList {
            val entries = mutableListOf<ValueDigest>()
            decoder.decodeStructure(descriptor) {
                var key = 0
                var value: ByteArray
                while (true) {
                    val index = decodeElementIndex(descriptor)
                    if (index == CompositeDecoder.DECODE_DONE) {
                        break
                    } else if (index % 2 == 0) {
                        key = decodeIntElement(descriptor, index)
                    } else if (index % 2 == 1) {
                        value = decodeSerializableElement(descriptor, index, ByteArraySerializer())
                        entries += ValueDigest(key.toUInt(), value)
                    }
                }
            }
            return ValueDigestList(entries)
        }
    }

    /**
     *  This serializer is needed for JsonBsonEncoder (persisting this in Mongo DB)
     *  Maybe this should be the default serializer, but at the moment it is not
     *  so easy to register contextual serializers in the waltid-cose lib
     */
    object ValueDigestListSerializer : KSerializer<ValueDigestList> {

        @OptIn(ExperimentalSerializationApi::class)
        override val descriptor: SerialDescriptor = mapSerialDescriptor(
            keyDescriptor = PrimitiveSerialDescriptor("key", PrimitiveKind.STRING),
            valueDescriptor = listSerialDescriptor<Byte>(),
        )

        override fun serialize(encoder: Encoder, value: ValueDigestList) {
            encoder.encodeStructure(descriptor) {
                var index = 0
                value.entries.forEach {
                    this.encodeStringElement(descriptor, index++, it.key.toString())
                    this.encodeSerializableElement(descriptor, index++, ByteArraySerializer(), it.value)
                }
            }
        }

        override fun deserialize(decoder: Decoder): ValueDigestList {
            val entries = mutableListOf<ValueDigest>()
            decoder.decodeStructure(descriptor) {
                var key = 0
                var value: ByteArray
                while (true) {
                    val index = decodeElementIndex(descriptor)
                    if (index == CompositeDecoder.DECODE_DONE) {
                        break
                    } else if (index % 2 == 0) {
                        key = decodeStringElement(descriptor, index).toInt()
                    } else if (index % 2 == 1) {
                        value = decodeSerializableElement(descriptor, index, ByteArraySerializer())
                        entries += ValueDigest(key.toUInt(), value)
                    }
                }
            }
            return ValueDigestList(entries)
        }
    }
}
