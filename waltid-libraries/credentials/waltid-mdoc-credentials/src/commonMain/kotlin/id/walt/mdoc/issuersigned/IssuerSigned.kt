package id.walt.mdoc.issuersigned

import id.walt.mdoc.cose.COSESign1
import id.walt.mdoc.dataelement.*
import kotlinx.serialization.Serializable

/**
 * Issuer signed part of the mdoc
 * Use MDocBuilder to create and add issuer signed items
 * @param nameSpaces Items name spaces, with CBOR encoded items
 * @param issuerAuth Issuer authentication COSE Sign1 structure
 */
@Serializable
data class IssuerSigned(
    val nameSpaces: Map<String, List<EncodedCBORElement>>?,
    val issuerAuth: COSESign1?
) {
    /**
     * Convert to CBOR map element
     */
    fun toMapElement() = MapElement(
        buildMap {
            nameSpaces?.let {
                put(MapKey("nameSpaces"), it.map { entry ->
                    Pair(MapKey(entry.key), ListElement(entry.value))
                }.toMap().toDE())
            }
            issuerAuth?.let {
                put(MapKey("issuerAuth"), it.data.toDE())
            }
        }
    )

    companion object {
        /**
         * Convert from CBOR map element
         */
        fun fromMapElement(mapElement: MapElement) = IssuerSigned(
            mapElement.value[MapKey("nameSpaces")]?.let {
                (it as MapElement).value.map { entry -> Pair(
                    entry.key.str,
                    (entry.value as ListElement).value.map { item -> item as EncodedCBORElement }) }.toMap()
            },
            mapElement.value[MapKey("issuerAuth")]?.let {
                COSESign1((it as ListElement).value)
            }
        )
    }
}
