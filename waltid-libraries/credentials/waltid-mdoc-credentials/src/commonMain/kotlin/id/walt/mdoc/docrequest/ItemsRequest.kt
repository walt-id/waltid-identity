package id.walt.mdoc.docrequest

import id.walt.mdoc.dataelement.MapElement
import id.walt.mdoc.dataelement.MapKey
import id.walt.mdoc.dataelement.StringElement
import id.walt.mdoc.dataelement.toDataElement
import kotlinx.serialization.Serializable

/**
 * Request for items of a given doc type, that's part of the MDoc request.
 * Note: Use MDocRequestBuilder and MDocRequest::getRequestedItemsFor to create or read requested items.
 * @param docType Document type requested
 * @param nameSpaces Name spaces and requested data elements
 */
@ConsistentCopyVisibility
@Serializable
data class ItemsRequest internal constructor(
    val docType: StringElement,
    val nameSpaces: MapElement
) {
    /**
     * Convert to CBOR map element
     */
    fun toMapElement() = buildMap {
        put(MapKey("docType"), docType)
        put(MapKey("nameSpaces"), nameSpaces)
    }.toDataElement()

}

