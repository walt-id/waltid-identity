package id.walt.mdoc.doc

import id.walt.mdoc.dataelement.MapElement

data class MDocNameSpace(
    val nameSpaceId: String,
    val claimsMap: MapElement,
) {

    fun toListOfItemToBeSigned() = claimsMap.value.map {
        ItemToBeSigned(
            nameSpace = nameSpaceId,
            elementIdentifier = it.key.str,
            elementValue = it.value,
        )
    }
}
