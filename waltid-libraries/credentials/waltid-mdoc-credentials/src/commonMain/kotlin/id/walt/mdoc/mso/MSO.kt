package id.walt.mdoc.mso

import id.walt.mdoc.dataelement.*
import id.walt.mdoc.issuersigned.IssuerSignedItem
import kotlinx.serialization.Serializable

/**
 * Mobile security object, representing the payload of the issuer signature, for the issuer signed part of the mdoc.
 * @param version Version of the MSO
 * @param digestAlgorithm digest algorithm used
 * @param valueDigests  Digests of the signed items
 * @param deviceKeyInfo Device key info, containing the mdoc authentication public key and related information
 * @param docType Document type
 * @param validityInfo Time-wise validity information about this MSO
 * @param status MSO status
 */
@Serializable
class MSO(
    val version: StringElement,
    val digestAlgorithm: StringElement,
    val valueDigests: MapElement,
    val deviceKeyInfo: DeviceKeyInfo,
    val docType: StringElement,
    val validityInfo: ValidityInfo,
    val status: Status? = null,
) {
    /**
     * Get map from digestID to digest value, for the items of the given name space
     * @param nameSpace Items name space
     * @return Key-value map of digest IDs and digest values
     */
    fun getValueDigestsFor(nameSpace: String): Map<Int, ByteArray> {
        val nameSpaceElement = valueDigests.value[MapKey(nameSpace)] ?: return mapOf()
        return (nameSpaceElement as MapElement).value.map { entry ->
            Pair(entry.key.int, (entry.value as ByteStringElement).value)
        }.toMap()
    }

    /**
     * Items name spaces
     */
    val nameSpaces
        get() = valueDigests.value.keys.map { it.str }

    /**
     * Convert to CBOR map element
     */
    fun toMapElement() = MapElement(
        buildMap {
            put(MapKey("version"), version)
            put(MapKey("digestAlgorithm"), digestAlgorithm)
            put(MapKey("valueDigests"), valueDigests)
            put(MapKey("deviceKeyInfo"), deviceKeyInfo.toMapElement())
            put(MapKey("docType"), docType)
            put(MapKey("validityInfo"), validityInfo.toMapElement())
            status?.let {
                put(MapKey("status"), status.toMapElement())
            }
        }
    )

    /**
     * Decode and verify the given items of the given name space
     * @param nameSpace The items name space
     * @param items the encoded items to verify
     * @return True if the items have been verified
     */
    fun verifySignedItems(nameSpace: String, items: List<EncodedCBORElement>): Boolean {
        val msoDigests = getValueDigestsFor(nameSpace)
        val algorithm = DigestAlgorithm.entries.first { it.value == digestAlgorithm.value }
        return items.all {
            val digestId = it.decode<IssuerSignedItem>().digestID.value.toInt()
            return msoDigests.containsKey(digestId) && msoDigests[digestId]!!.contentEquals(digestItem(it, algorithm))
        }
    }

    companion object {
        /**
         * Create item digest, for the given item
         */
        fun digestItem(encodedItem: EncodedCBORElement, digestAlgorithm: DigestAlgorithm): ByteArray {
            return digestAlgorithm.getHasher().digest(encodedItem.toCBOR()).bytes
        }

        /**
         * Create MSO for the given items and parameters
         * @param nameSpaces Name spaces of issuer signed items, that should be protected by this MSO
         * @param deviceKeyInfo Device key info of the document holder
         * @param docType Document type
         * @param validityInfo Time-wise validity information of this MSO
         * @param digestAlgorithm Digest algorithm, defaults to SHA-256
         * @param status MSO status
         * @return The Mobile security object, protecting the given data
         */
        fun createFor(
            nameSpaces: Map<String, List<IssuerSignedItem>>,
            deviceKeyInfo: DeviceKeyInfo,
            docType: String,
            validityInfo: ValidityInfo,
            digestAlgorithm: DigestAlgorithm = DigestAlgorithm.SHA256,
            status: Status? = null,
        ): MSO {
            return MSO(
                "1.0".toDataElement(),
                digestAlgorithm.value.toDataElement(),
                nameSpaces.mapValues { entry ->
                    entry.value.associate { item ->
                        Pair(
                            item.digestID.value.toInt(),
                            ByteStringElement(
                                digestItem(EncodedCBORElement(item.toMapElement()), digestAlgorithm)
                            )
                        )
                    }.toDataElement()
                }.toDataElement(),
                deviceKeyInfo,
                docType.toDataElement(),
                validityInfo,
                status = status,
            )
        }
    }
}
