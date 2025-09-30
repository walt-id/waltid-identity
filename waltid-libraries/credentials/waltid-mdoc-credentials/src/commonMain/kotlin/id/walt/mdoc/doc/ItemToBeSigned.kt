package id.walt.mdoc.doc

import id.walt.mdoc.dataelement.DataElement

data class ItemToBeSigned(
    val nameSpace: String,
    val elementIdentifier: String,
    val elementValue: DataElement,
)
