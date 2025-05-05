package id.walt.mdoc.deviceengagement.retrieval.options

import id.walt.mdoc.dataelement.DataElement

sealed class RetrievalOptions {

    abstract fun toDataElement(): DataElement

    /**
     * Serialize to CBOR data
     */
    fun toCBOR() = toDataElement().toCBOR()

    /**
     * Serialize to CBOR hex string
     */
    fun toCBORHex() = toDataElement().toCBORHex()

}