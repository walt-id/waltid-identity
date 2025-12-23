@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.mdoc.objects.elements

import id.walt.cose.coseCompliantCbor
import id.walt.mdoc.encoding.ByteStringWrapper
import id.walt.mdoc.objects.wrapInCborTag
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray

/**
 * Represents the list of issuer-signed items for a single namespace.
 *
 * In the ISO/IEC 18013-5 specification, the data for each namespace within the `IssuerSigned`
 * structure is a CBOR array of `IssuerSignedItemBytes`. This class provides a type-safe,
 * object-oriented wrapper for that array.
 *
 * @see ISO/IEC 18013-5:xxxx(E), 8.3.2.1.2.3 (Device retrieval mdoc response)
 *
 * @property entries A list of `ByteStringWrapper<IssuerSignedItem>`, where each wrapper contains both the
 * deserialized [IssuerSignedItem] object and its original serialized bytes (`IssuerSignedItemBytes`),
 * which is essential for digest verification.
 */
data class IssuerSignedList(
    val entries: List<ByteStringWrapper<IssuerSignedItem>>,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as IssuerSignedList

        return entries == other.entries
    }

    override fun hashCode(): Int = 31 * entries.hashCode()

    companion object {
        /**
         * Factory method to create an [IssuerSignedList] from a list of [IssuerSignedItem] objects.
         *
         * This method correctly serializes each [IssuerSignedItem] into its `IssuerSignedItemBytes`
         * representation, which is a bytestring of the item's CBOR encoding, wrapped in CBOR Tag 24.
         *
         * @see ISO/IEC 18013-5:xxxx(E), 8.3.2.1.2.3 (CDDL for IssuerSignedItemBytes)
         *
         * @param items The list of [IssuerSignedItem] objects to be included.
         * @return A new [IssuerSignedList] instance with correctly serialized byte wrappers.
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun fromIssuerSignedItems(items: List<IssuerSignedItem>, namespace: String) =
            IssuerSignedList(items.map { item ->
                ByteStringWrapper(
                    item,
                    coseCompliantCbor.encodeToByteArray(item.serialize(namespace)).wrapInCborTag(24)
                )
            })

        private fun IssuerSignedItem.serialize(namespace: String): ByteArray =
            coseCompliantCbor.encodeToByteArray(IssuerSignedItemSerializer(namespace, elementIdentifier), this)
    }
}
