package id.walt.sdjwt

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

private val log = KotlinLogging.logger { }

/**
 * Selective disclosure map, that describes for each payload field recursively, whether it should be selectively disclosable / selected for disclosure.
 * @param fields  map of field keys to SD field descriptors
 * @param decoyMode **For SD-JWT issuance:** Generate decoy digests for this hierarchical level randomly or fixed, set to NONE for parsed SD-JWTs, **for presentation:** _unused_
 * @param decoys  **For SD-JWT issuance:** Num (fixed mode) or max num (random mode) of decoy digests to add for this hierarchical level. 0 if NONE, **for presentation:** _unused_
 */
@Serializable
class SDMap(
    val fields: Map<String, SDField>,
    val decoyMode: DecoyMode = DecoyMode.NONE,
    val decoys: Int = 0,
) : Map<String, SDField> {
    override val entries: Set<Map.Entry<String, SDField>>
        get() = fields.entries
    override val keys: Set<String>
        get() = fields.keys
    override val size: Int
        get() = fields.size
    override val values: Collection<SDField>
        get() = fields.values

    override fun isEmpty() = fields.isEmpty()

    override fun get(key: String) = fields[key]

    override fun containsValue(value: SDField) = fields.containsValue(value)

    override fun containsKey(key: String) = fields.containsKey(key)

    fun prettyPrint(indentBy: Int = 0): String {
        val indentation = (0).rangeTo(indentBy).joinToString(" ") { "" }
        return "$indentation + with decoys: $decoyMode (${decoys})\n" + keys.flatMap { key ->
            listOfNotNull(
                "${indentation}- $key: ${fields[key]?.sd == true}",
                fields[key]?.children?.prettyPrint(indentBy + 2)
            )
        }.joinToString("\n")
    }

    fun toJSON(): JsonObject {
        return buildJsonObject {
            put("fields", buildJsonObject {
                fields.forEach { entry ->
                    put(entry.key, entry.value.toJSON())
                }
            })
            put("decoyMode", decoyMode.name)
            put("decoys", decoys)
        }
    }

    companion object {

        /**
         * Generate SDMap by comparing the fully disclosed payload with the undisclosed payload
         * @param fullPayload Full payload containing all fields
         * @param undisclosedPayload  Payload with selectively disclosable fields removed
         * @param decoyMode **For SD-JWT issuance:** Generate decoy digests for this hierarchical level randomly or fixed, set to NONE for parsed SD-JWTs, **for presentation:** _unused_
         * @param decoys  **For SD-JWT issuance:** Num (fixed mode) or max num (random mode) of decoy digests to add for this hierarchical level. 0 if NONE, **for presentation:** _unused_.
         */
        fun generateSDMap(
            fullPayload: JsonObject,
            undisclosedPayload: JsonObject,
            decoyMode: DecoyMode = DecoyMode.NONE,
            decoys: Int = 0,
        ): SDMap {
            return fullPayload.mapValues { entry ->
                val full = entry.value
                val undisclosed = undisclosedPayload[entry.key]
                when {
                    !undisclosedPayload.containsKey(entry.key) -> SDField(true)
                    full is JsonObject && undisclosed is JsonObject ->
                        SDField(false, generateSDMap(full, undisclosed, decoyMode, decoys))
                    full is JsonArray && undisclosed is JsonArray ->
                        SDField(false, arrayChildren = generateSDArray(full, undisclosed, decoyMode, decoys))
                    else -> SDField(false)
                }
            }.toSDMap(decoyMode, decoys)
        }

        /**
         * Wrappers in [undisclosedArray] mark the matching [fullArray] index as `sd = true`.
         * If sizes differ (decoys interleaved), fall back to a non-disclosable [SDArray].
         */
        private fun generateSDArray(
            fullArray: JsonArray,
            undisclosedArray: JsonArray,
            decoyMode: DecoyMode,
            decoys: Int,
        ): SDArray {
            if (fullArray.size != undisclosedArray.size) {
                return SDArray(
                    elements = List(fullArray.size) { SDField(false) },
                    decoyMode = decoyMode,
                    decoys = decoys,
                )
            }
            val elements = fullArray.zip(undisclosedArray) { full, wire ->
                when {
                    arrayElementWrapperHash(wire) != null -> SDField(true)
                    full is JsonObject && wire is JsonObject ->
                        SDField(false, generateSDMap(full, wire, decoyMode, decoys))
                    full is JsonArray && wire is JsonArray ->
                        SDField(false, arrayChildren = generateSDArray(full, wire, decoyMode, decoys))
                    else -> SDField(false)
                }
            }
            return SDArray(elements = elements, decoyMode = decoyMode, decoys = decoys)
        }

        /**
         * Generate SDMap based on a set of simplified JSON paths.
         *
         * Supported segment forms:
         * - named keys, e.g. `credentialSubject`, `firstName`
         * - specific array index, e.g. `nationalities.[0]`
         * - all array elements (wildcard), e.g. `colors.[]`
         *
         * Array index segments must be preceded by a dot: `"colors.[0]"` is valid,
         * `"colors[0]"` is **not**.
         *
         * Examples:
         * - `"credentialSubject.firstName"`
         * - `"nationalities.[0]"`
         * - `"cars.[].make"`
         * - `"contacts.[0].[2]"`
         *
         * @throws IllegalArgumentException for malformed segments or for paths that mix named
         *   keys and array indices at the same hierarchy level.
         */
        fun generateSDMap(jsonPaths: Collection<String>, decoyMode: DecoyMode = DecoyMode.NONE, decoys: Int = 0): SDMap {
            val tokenized = jsonPaths.map { PathToken.tokenize(it) }
            return buildSDMap(tokenized, decoyMode, decoys)
        }

        private fun buildSDMap(paths: List<List<PathToken>>, decoyMode: DecoyMode, decoys: Int): SDMap {
            val groups = paths.groupByFirstToken()
            require(groups.keys.all { it is PathToken.Key }) {
                "Cannot mix named keys and array indices at the same path level: ${groups.keys}"
            }
            val fields = groups.entries.associate { (token, tails) ->
                (token as PathToken.Key).name to fieldFromTails(tails, decoyMode, decoys)
            }
            return SDMap(fields, decoyMode, decoys)
        }

        private fun buildSDArray(paths: List<List<PathToken>>, decoyMode: DecoyMode, decoys: Int): SDArray {
            val groups = paths.groupByFirstToken()
            require(groups.keys.all { it is PathToken.Index || it is PathToken.Wildcard }) {
                "Cannot mix array indices and named keys at the same path level: ${groups.keys}"
            }
            val wildcardTails = groups[PathToken.Wildcard].orEmpty()
            val wildcard = wildcardTails.takeIf { it.isNotEmpty() }
                ?.let { fieldFromTails(it, decoyMode, decoys) }
            val maxIndex = groups.keys.filterIsInstance<PathToken.Index>().maxOfOrNull { it.value } ?: -1
            // Each index inherits the wildcard's tails on top of any explicit ones, so
            // `[0].city` + `[].zip` discloses both city and zip at index 0.
            val elements = (0..maxIndex).map { idx ->
                val merged = groups[PathToken.Index(idx)].orEmpty() + wildcardTails
                if (merged.isEmpty()) SDField(false) else fieldFromTails(merged, decoyMode, decoys)
            }
            return SDArray(elements, wildcard, decoyMode, decoys)
        }

        private fun fieldFromTails(
            tails: List<List<PathToken>>,
            decoyMode: DecoyMode,
            decoys: Int,
        ): SDField {
            val isLeaf = tails.any { it.isEmpty() }
            val deeper = tails.filter { it.isNotEmpty() }
            if (deeper.isEmpty()) return SDField(isLeaf)
            return when (deeper.first().first()) {
                is PathToken.Key -> SDField(sd = isLeaf, children = buildSDMap(deeper, decoyMode, decoys))
                is PathToken.Index, PathToken.Wildcard ->
                    SDField(sd = isLeaf, arrayChildren = buildSDArray(deeper, decoyMode, decoys))
            }
        }

        private fun regenerateSDField(
            sd: Boolean,
            value: JsonElement,
            digestedDisclosure: Map<String, SDisclosure>
        ): SDField {
            return SDField(
                sd,
                children = if (value is JsonObject) regenerateSDMap(value.jsonObject, digestedDisclosure) else null,
                arrayChildren = if (value is JsonArray) regenerateSDArray(value, digestedDisclosure) else null,
            )
        }

        /**
         * Regenerate [SDArray] from a wire array — a structural view used by the `sdMap`
         * accessor. [SDArray.elements] uses logical indexing: wrappers whose digest doesn't
         * resolve, or that resolve to a non-array disclosure, are skipped (no logical
         * position). For strict-mode validation use [SDPayload.fullPayload] /
         * [SDPayload.verifyDisclosures].
         */
        internal fun regenerateSDArray(
            array: JsonArray,
            digestedDisclosures: Map<String, SDisclosure>,
        ): SDArray {
            val elements = mutableListOf<SDField>()
            array.forEach { element ->
                val wrapperHash = arrayElementWrapperHash(element)
                if (wrapperHash != null) {
                    val disclosure = digestedDisclosures[wrapperHash] as? ArrayElementDisclosure
                    if (disclosure != null) {
                        elements += regenerateSDField(true, disclosure.value, digestedDisclosures)
                    }
                } else {
                    elements += regenerateSDField(false, element, digestedDisclosures)
                }
            }
            return SDArray(elements = elements)
        }

        /**
         * Regenerate [SDMap] from an undisclosed payload and disclosures map — a structural
         * view used by the `sdMap` accessor. Disclosed × plain and disclosed × disclosed
         * claim-name collisions are not enforced here; for strict-mode validation use
         * [SDPayload.fullPayload].
         */
        internal fun regenerateSDMap(
            undisclosedPayload: JsonObject,
            digestedDisclosures: Map<String, SDisclosure>
        ): SDMap {
            val disclosed = undisclosedPayload[SDJwt.DIGESTS_KEY]?.jsonArray
                ?.filter { digestedDisclosures.containsKey(it.jsonPrimitive.content) }
                ?.map { digestedDisclosures[it.jsonPrimitive.content]!! }
                ?.filterIsInstance<ObjectPropertyDisclosure>()
                ?.associateBy({ it.key }, { regenerateSDField(true, it.value, digestedDisclosures) })
                ?: emptyMap()
            val plain = undisclosedPayload.filterNot { it.key == SDJwt.DIGESTS_KEY }.mapValues {
                regenerateSDField(false, it.value, digestedDisclosures)
            }
            return (disclosed + plain).toSDMap()
        }

        fun fromJSON(json: JsonObject): SDMap {
            log.trace { "Parsing SDMap from: $json" }
            return SDMap(
                fields = json["fields"]?.jsonObject?.entries?.associate { entry ->
                    Pair(entry.key, SDField.fromJSON(entry.value))
                } ?: mapOf(),
                decoyMode = json["decoyMode"]?.let { DecoyMode.fromJSON(it) } ?: DecoyMode.NONE,
                decoys = json["decoys"]?.jsonPrimitive?.int ?: 0
            )
        }

        fun fromJSON(json: String): SDMap {
            return fromJSON(Json.parseToJsonElement(json).jsonObject)
        }
    }

}

/**
 * Convert a Map<String, SDField> to SDMap object, with the given optional decoy parameters
 * @param decoyMode **For SD-JWT issuance:** Generate decoy digests for this hierarchical level randomly or fixed, set to NONE for parsed SD-JWTs, **for presentation:** _unused_
 * @param decoys  **For SD-JWT issuance:** Num (fixed mode) or max num (random mode) of decoy digests to add for this hierarchical level. 0 if NONE, **for presentation:** _unused_.
 */
fun Map<String, SDField>.toSDMap(decoyMode: DecoyMode = DecoyMode.NONE, decoys: Int = 0): SDMap {
    return SDMap(this, decoyMode, decoys)
}
