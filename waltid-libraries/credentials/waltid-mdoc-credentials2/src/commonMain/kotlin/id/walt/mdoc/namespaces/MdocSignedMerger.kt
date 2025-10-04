package id.walt.mdoc.namespaces

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Merges signed mdoc namespaces (especially issuerSigned & deviceSigned)
 * All device signed namespace elements have to be validated beforehand.
 */
object MdocSignedMerger {

    private fun MutableMap<String, MutableMap<String, JsonElement>>.putMerging(
        other: Map<String, JsonObject>,
        strategy: MdocDuplicatesMergeStrategy
    ) {
        other.forEach { (namespace, obj) ->
            if (containsKey(namespace)) {
                val namespaceMap = get(namespace)!!
                obj.forEach { (elementIdentifier, elementValue) ->
                    val isDuplicated = namespaceMap.contains(elementIdentifier)
                    when (strategy) {
                        MdocDuplicatesMergeStrategy.USE_FIRST if isDuplicated -> {}
                        MdocDuplicatesMergeStrategy.CLASH if isDuplicated -> {
                            throw IllegalArgumentException("Failed merging mdoc credential namespaces: Namespace $namespace already contains elementIdentifier $elementIdentifier")
                        }

                        else -> {
                            namespaceMap[elementIdentifier] = elementValue
                        }
                    }
                }
            } else {
                this[namespace] = obj.toMap().toMutableMap()
            }
        }
    }

    enum class MdocDuplicatesMergeStrategy {
        /** Throw error if elementIdentifier is contained in multiple document versions (mDL standard behaviour) */
        CLASH,

        /** Override elementIdentifier (e.g. override stale issuerSigned data with fresh deviceSigned data) */
        OVERRIDE,

        /** Use first (e.g. ignore deviceSigned data if issuerSigned data is available) */
        USE_FIRST
    }

    /**
     * Merge multiple Mdoc namespaces elements (e.g. deviceSigned namespaces, issuerSigned namespaces) into result mdoc JSON
     * Applies MdocDuplicatesMergeStrategy
     */
    fun merge(vararg namespaceElement: JsonObject, strategy: MdocDuplicatesMergeStrategy): JsonObject =
        LinkedHashMap<String, MutableMap<String, JsonElement>>().apply {
            namespaceElement.forEach { element ->
                putMerging(other = element.mapValues { it.value.jsonObject }, strategy = strategy)
            }
        }.run { JsonObject(mapValues { (_, v) -> JsonObject(v) }) }

}
