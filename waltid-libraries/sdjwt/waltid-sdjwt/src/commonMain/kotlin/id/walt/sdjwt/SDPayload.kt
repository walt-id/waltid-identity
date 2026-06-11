package id.walt.sdjwt

import dev.whyoleg.cryptography.random.CryptographyRandom
import id.walt.sdjwt.utils.Base64Utils.encodeToBase64Url
import id.walt.sdjwt.utils.SdjwtStringUtils.decodeFromBase64Url
import kotlinx.serialization.json.*
import org.kotlincrypto.hash.sha2.SHA256
import org.kotlincrypto.random.CryptoRand
import kotlin.io.encoding.Base64
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.random.Random

/**
 * Payload object of the SD-JWT, representing the undisclosed payload from the JWT body and the selective disclosures, appended to the JWT token
 * @param undisclosedPayload  Undisclosed payload JSON object, as contained in the JWT body
 * @param digestedDisclosures Map of digests to parsed disclosures, which are appended to the JWT token
 */
@ConsistentCopyVisibility
@Suppress("NON_EXPORTABLE_TYPE")
@OptIn(ExperimentalJsExport::class)
@JsExport
data class SDPayload internal constructor(
    val undisclosedPayload: JsonObject,
    val digestedDisclosures: Map<String, SDisclosure> = mapOf(),
) {
    /**
     * Flat list of parsed disclosures, appended to the JWT token
     */
    val sDisclosures
        get() = digestedDisclosures.values

    /** Full payload, with all selectively-disclosed claims resolved recursively. */
    val fullPayload: JsonObject get() = walk(select = null).resolved

    /** SDMap regenerated from undisclosed payload and disclosures. */
    val sdMap get() = SDMap.regenerateSDMap(undisclosedPayload, digestedDisclosures)

    /** Returns a new [SDPayload] keeping only disclosures the holder selected via [sdMap]. */
    fun withSelectiveDisclosures(sdMap: Map<String, SDField>): SDPayload {
        val released = walk(select = sdMap).released
        return SDPayload(
            undisclosedPayload = undisclosedPayload,
            digestedDisclosures = digestedDisclosures.filterValues { it.disclosure in released },
        )
    }

    /** Mutable state threaded through one [walk]. */
    private class WalkState(
        /** Disclosures not yet seen on the wire; anything left after the walk is unreferenced. */
        val unconsumed: MutableMap<String, SDisclosure>,
        /** Every digest seen on the wire; detects duplicates across the whole tree. */
        val seenDigests: MutableSet<String> = mutableSetOf(),
        /** Disclosure strings the walk chose to reveal. */
        val released: MutableSet<String> = mutableSetOf(),
    )

    private class WalkResult(val resolved: JsonObject, val released: Set<String>)

    /**
     * Walks the payload once, shared by verifier and holder paths.
     * `select == null` reveals everything; otherwise reveals only claims marked `sd = true`.
     */
    private fun walk(select: Map<String, SDField>?): WalkResult {
        val state = WalkState(unconsumed = digestedDisclosures.toMutableMap())
        val resolved = walkObject(undisclosedPayload, select, state)
        if (state.unconsumed.isNotEmpty()) {
            throw SDJwtVerificationException("${state.unconsumed.size} disclosure(s) not referenced by any digest")
        }
        return WalkResult(resolved, state.released)
    }

    private fun walkObject(
        payload: JsonObject,
        select: Map<String, SDField>?,
        state: WalkState,
    ): JsonObject = buildJsonObject {
        payload.forEach { entry ->
            when (entry.key) {
                SDJwt.DIGESTS_KEY -> {
                    val digestArray = entry.value as? JsonArray
                        ?: throw SDJwtVerificationException("${SDJwt.DIGESTS_KEY} must be a JSON array")
                    digestArray.forEach { unveilObjectDisclosure(digestString(it), select, state, this) }
                }
                ARRAY_ELEMENT_WRAPPER_KEY ->
                    throw SDJwtVerificationException("Reserved name '${entry.key}' used as a plain claim")
                else -> putUnique(
                    entry.key,
                    recurse(entry.value, select?.get(entry.key), select == null, state),
                )
            }
        }
    }

    private fun unveilObjectDisclosure(
        digest: String,
        select: Map<String, SDField>?,
        state: WalkState,
        builder: JsonObjectBuilder,
    ) {
        if (!state.seenDigests.add(digest)) {
            throw SDJwtVerificationException("Duplicate digest reference: $digest")
        }
        // Decoy or holder-stripped: nothing to reveal.
        val disc = state.unconsumed.remove(digest) ?: return
        when (disc) {
            is ArrayElementDisclosure -> throw SDJwtVerificationException(
                "Expected an object-property disclosure, got an array-element disclosure"
            )
            is ObjectPropertyDisclosure -> {
                if (disc.key == SDJwt.DIGESTS_KEY || disc.key == ARRAY_ELEMENT_WRAPPER_KEY) {
                    throw SDJwtVerificationException("Reserved name '${disc.key}' used as a disclosed claim")
                }
                val release = select == null || select[disc.key]?.sd == true
                if (!release) return
                state.released += disc.disclosure
                builder.putUnique(disc.key, recurse(disc.value, select?.get(disc.key), select == null, state))
            }
        }
    }

    /**
     * Walks an array. [sdArray] indexes by **logical** position (decoys don't advance it).
     * `null` reveals everything; otherwise reveals only elements marked `sd = true`.
     */
    private fun walkArray(
        array: JsonArray,
        sdArray: SDArray?,
        state: WalkState,
    ): JsonArray = buildJsonArray {
        var logicalIndex = 0
        array.forEach { element ->
            val digest = arrayElementWrapperHash(element)
            if (digest != null) {
                if (!state.seenDigests.add(digest)) {
                    throw SDJwtVerificationException("Duplicate digest reference: $digest")
                }
                when (val disc = state.unconsumed.remove(digest)) {
                    is ObjectPropertyDisclosure -> throw SDJwtVerificationException(
                        "Expected an array-element disclosure, got an object-property disclosure"
                    )
                    is ArrayElementDisclosure -> {
                        val field = sdArray?.fieldAt(logicalIndex)
                        logicalIndex += 1
                        val release = sdArray == null || field?.sd == true
                        if (release) {
                            state.released += disc.disclosure
                            add(recurse(disc.value, field, sdArray == null, state))
                        }
                    }
                    null -> { /* decoy or holder-stripped: drop the slot. */ }
                }
            } else {
                val field = sdArray?.fieldAt(logicalIndex)
                logicalIndex += 1
                add(recurse(element, field, sdArray == null, state))
            }
        }
    }

    private fun SDArray.fieldAt(logicalIndex: Int): SDField? =
        elements.getOrNull(logicalIndex) ?: wildcard

    /**
     * Recurse into a nested value. [releaseAll] carries verifier mode down the tree;
     * when off, an absent [field] means "reveal nothing under this branch".
     */
    private fun recurse(
        value: JsonElement,
        field: SDField?,
        releaseAll: Boolean,
        state: WalkState,
    ): JsonElement = when (value) {
        is JsonObject -> walkObject(value, if (releaseAll) null else (field?.children ?: emptyMap()), state)
        is JsonArray -> walkArray(value, if (releaseAll) null else (field?.arrayChildren ?: SDArray()), state)
        else -> value
    }

    private fun digestString(node: JsonElement): String {
        val p = node as? JsonPrimitive
        if (p == null || !p.isString) {
            throw SDJwtVerificationException("${SDJwt.DIGESTS_KEY} entries must be JSON strings")
        }
        return p.content
    }

    private fun JsonObjectBuilder.putUnique(key: String, value: JsonElement) {
        if (put(key, value) != null) {
            throw SDJwtVerificationException("Duplicate claim name '$key' at the same object level")
        }
    }

    /**
     * Payload with all selectively dislosable fields filtered out (all fields undisclosed)
     */
    fun withoutDisclosures(): SDPayload {
        return SDPayload(undisclosedPayload, mapOf())
    }

    /** Returns `false` if [fullPayload] would throw any [SDJwtVerificationException]. */
    fun verifyDisclosures(): Boolean = try {
        fullPayload
        true
    } catch (_: SDJwtVerificationException) {
        false
    }

    @JsExport.Ignore // see SDPayloadBuilder for JS support
    companion object {
        /** Marker key inside an array element that names a digest. */
        internal const val ARRAY_ELEMENT_WRAPPER_KEY = "..."

        val sha256 = SHA256()

        private fun digest(value: String): String {
            val messageDigest = sha256.digest(value.encodeToByteArray())
            return messageDigest.encodeToBase64Url()
        }

        class CryptoRandAsKotlinRandom(private val cryptoRand: CryptoRand) : Random() {
            override fun nextBits(bitCount: Int): Int {
                if (bitCount == 0) return 0

                // Fetch 4 bytes from custom interface
                val buf = ByteArray(4)
                cryptoRand.nextBytes(buf)

                // Assemble the 4 bytes into a single 32-bit integer
                val result = ((buf[0].toInt() and 0xFF) shl 24) or
                        ((buf[1].toInt() and 0xFF) shl 16) or
                        ((buf[2].toInt() and 0xFF) shl 8) or
                        (buf[3].toInt() and 0xFF)

                // shift right to return only the requested number of bits
                return result ushr (32 - bitCount)
            }
        }

        private val secureRandom = CryptoRandAsKotlinRandom(CryptoRand.Default)

        private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

        private fun generateSalt(): String {
            val randomness = CryptographyRandom.nextBytes(16)
            return base64Url.encode(randomness)
        }

        private fun generateDisclosure(key: String, value: JsonElement): SDisclosure {
            val salt = generateSalt()
            return base64Url.encode(buildJsonArray {
                add(salt)
                add(key)
                add(value)
            }.toString().encodeToByteArray()).let { disclosure ->
                ObjectPropertyDisclosure(
                    disclosure = disclosure,
                    salt = salt,
                    key = key,
                    value = value
                )
            }
        }

        /** A 2-element disclosure `[salt, value]` for an array element. */
        private fun generateArrayElementDisclosure(value: JsonElement): ArrayElementDisclosure {
            val salt = generateSalt()
            val encoded = base64Url.encode(buildJsonArray {
                add(salt)
                add(value)
            }.toString().encodeToByteArray())
            return ArrayElementDisclosure(disclosure = encoded, salt = salt, value = value)
        }

        private fun digestSDClaim(
            key: String,
            value: JsonElement,
            digests2disclosures: MutableMap<String, SDisclosure>
        ): String {
            val disclosure = generateDisclosure(key, value)
            return digest(disclosure.disclosure).also {
                digests2disclosures[it] = disclosure
            }
        }

        private fun digestArrayElement(
            value: JsonElement,
            digests2disclosures: MutableMap<String, SDisclosure>
        ): String {
            val disclosure = generateArrayElementDisclosure(value)
            return digest(disclosure.disclosure).also {
                digests2disclosures[it] = disclosure
            }
        }

        private fun removeSDFields(payload: JsonObject, sdMap: Map<String, SDField>): JsonObject {
            return JsonObject(payload.filterKeys { key -> sdMap[key]?.sd != true }.mapValues { entry ->
                if (entry.value is JsonObject && !sdMap[entry.key]?.children.isNullOrEmpty()) {
                    removeSDFields(entry.value.jsonObject, sdMap[entry.key]?.children ?: mapOf())
                } else {
                    entry.value
                }
            })
        }

        private fun generateSDPayload(
            payload: JsonObject,
            sdMap: SDMap,
            digests2disclosures: MutableMap<String, SDisclosure>
        ): JsonObject {
            val sdPayload = removeSDFields(payload, sdMap).toMutableMap()
            val digests = payload.filterKeys { key ->
                // iterate over all fields that are selectively disclosable AND/OR have nested fields that might be:
                sdMap[key]?.sd == true || !sdMap[key]?.children.isNullOrEmpty() || sdMap[key]?.arrayChildren != null
            }.map { entry ->
                val arrayChildren = sdMap[entry.key]?.arrayChildren
                if (entry.value is JsonArray && arrayChildren != null) {
                    // this field is an array with selectively disclosable elements:
                    val transformedArray = generateSDArray(
                        array = entry.value.jsonArray,
                        sdArray = arrayChildren,
                        digests2disclosures = digests2disclosures
                    )
                    if (sdMap[entry.key]?.sd == true) {
                        // the array itself is also selectively disclosable as a whole:
                        digestSDClaim(
                            key = entry.key,
                            value = transformedArray,
                            digests2disclosures = digests2disclosures
                        )
                    } else {
                        sdPayload[entry.key] = transformedArray
                        null
                    }
                } else if (entry.value !is JsonObject || sdMap[entry.key]?.children.isNullOrEmpty()) {
                    // this field has no nested elements and/or is selectively disclosable only as a whole:
                    digestSDClaim(
                        key = entry.key,
                        value = entry.value,
                        digests2disclosures = digests2disclosures
                    )
                } else {
                    // the nested properties could be selectively disclosable individually
                    // recursively generate SD payload for nested object:
                    val nestedSDPayload = generateSDPayload(
                        payload = entry.value.jsonObject,
                        sdMap = sdMap[entry.key]!!.children!!,
                        digests2disclosures = digests2disclosures
                    )

                    if (sdMap[entry.key]?.sd == true) {
                        // this nested object is also selectively disclosable as a whole
                        // so let's compute the digest and disclosure for the nested SD payload:
                        digestSDClaim(
                            key = entry.key,
                            value = nestedSDPayload,
                            digests2disclosures = digests2disclosures
                        )
                    } else {
                        // this nested object is not selectively disclosable as a whole, add the nested SD payload as it is:
                        sdPayload[entry.key] = nestedSDPayload
                        // no digest/disclosure is added for this field (though the nested properties may have generated digests and disclosures)
                        null
                    }
                }
            }.filterNotNull().toSet()

            if (digests.isNotEmpty()) {
                val mixed = digests.toMutableList()
                repeat(numDecoys(sdMap.decoyMode, sdMap.decoys)) { mixed += digest(generateSalt()) }
                // Shuffle so source claim order doesn't leak through _sd.
                sdPayload[SDJwt.DIGESTS_KEY] = JsonArray(mixed.shuffled(secureRandom).map { JsonPrimitive(it) })
            }
            return JsonObject(sdPayload)
        }

        /**
         * Replace each selectively-disclosable element of [array] with a `{"...": "<digest>"}`
         * wrapper. Decoys are inserted at random positions; real elements keep their relative
         * order.
         */
        private fun generateSDArray(
            array: JsonArray,
            sdArray: SDArray,
            digests2disclosures: MutableMap<String, SDisclosure>
        ): JsonArray {
            val transformed = array.mapIndexed { index, element ->
                val field = sdArray.elements.getOrNull(index) ?: sdArray.wildcard
                val nested = if (element is JsonObject && field?.children != null) {
                    generateSDPayload(element, field.children, digests2disclosures)
                } else if (element is JsonArray && field?.arrayChildren != null) {
                    generateSDArray(element, field.arrayChildren, digests2disclosures)
                } else element
                if (field?.sd == true) {
                    val hash = digestArrayElement(nested, digests2disclosures)
                    buildJsonObject { put(ARRAY_ELEMENT_WRAPPER_KEY, hash) }
                } else {
                    nested
                }
            }
            val result = transformed.toMutableList()
            repeat(numDecoys(sdArray.decoyMode, sdArray.decoys)) {
                val decoy = buildJsonObject { put(ARRAY_ELEMENT_WRAPPER_KEY, digest(generateSalt())) }
                result.add(secureRandom.nextInt(result.size + 1), decoy)
            }
            return JsonArray(result)
        }

        /** Number of decoys to add for one hierarchical level. */
        private fun numDecoys(mode: DecoyMode, decoys: Int): Int = when (mode) {
            DecoyMode.NONE -> 0
            DecoyMode.RANDOM -> if (decoys > 0) secureRandom.nextInt(decoys + 1) else 0
            DecoyMode.FIXED -> decoys
        }

        /**
         * Create SD payload object, based on full payload and disclosure map.
         * **Not supported on JavaScript**, use _SDPayloadBuilder_ instead.
         * @param fullPayload Full payload with all fields contained
         * @param disclosureMap SDMap indicating selective disclosure for each payload field recursively, and decoy properties for issuance
         */
        @JsExport.Ignore
        fun createSDPayload(fullPayload: JsonObject, disclosureMap: SDMap): SDPayload {
            val digestedDisclosures = mutableMapOf<String, SDisclosure>()
            return SDPayload(
                undisclosedPayload = generateSDPayload(
                    payload = fullPayload,
                    sdMap = disclosureMap,
                    digests2disclosures = digestedDisclosures
                ),
                digestedDisclosures = digestedDisclosures
            )
        }

        /**
         * Create SD payload with JWT claims set (from platform dependent claims set object) and disclosure map.
         * **Not supported on JavaScript**, use _SDPayloadBuilder_ instead.
         * @param jwtClaimsSet Full payload with all fields contained
         * @param disclosureMap SDMap indicating selective disclosure for each payload field recursively, and decoy properties for issuance
         */
        @JsExport.Ignore
        fun createSDPayload(jwtClaimsSet: JWTClaimsSet, disclosureMap: SDMap) =
            createSDPayload(
                fullPayload = Json.parseToJsonElement(jwtClaimsSet.toString()).jsonObject,
                disclosureMap = disclosureMap
            )

        /**
         * Create SD payload based on full payload and undisclosed payload.
         * **Not supported on JavaScript**, use _SDPayloadBuilder_ instead.
         * @param fullPayload Full payload containing all fields
         * @param undisclosedPayload  Payload with selectively disclosable fields removed
         * @param decoyMode **For SD-JWT issuance:** Generate decoy digests for this hierarchical level randomly or fixed, set to NONE for parsed SD-JWTs, **for presentation:** _unused_
         * @param decoys  **For SD-JWT issuance:** Num (fixed mode) or max num (random mode) of decoy digests to add for this hierarchical level. 0 if NONE, **for presentation:** _unused_.
         */
        @JsExport.Ignore
        fun createSDPayload(
            fullPayload: JsonObject,
            undisclosedPayload: JsonObject,
            decoyMode: DecoyMode = DecoyMode.NONE,
            decoys: Int = 0
        ) = createSDPayload(
            fullPayload = fullPayload,
            disclosureMap = SDMap.generateSDMap(
                fullPayload = fullPayload,
                undisclosedPayload = undisclosedPayload,
                decoyMode = decoyMode,
                decoys = decoys
            )
        )

        /**
         * Create SD payload based on full payload as JWT claims set and undisclosed payload.
         * **Not supported on JavaScript**, use _SDPayloadBuilder_ instead.
         * @param fullJWTClaimsSet Full payload containing all fields
         * @param undisclosedJWTClaimsSet  Payload with selectively disclosable fields removed
         * @param decoyMode **For SD-JWT issuance:** Generate decoy digests for this hierarchical level randomly or fixed, set to NONE for parsed SD-JWTs, **for presentation:** _unused_
         * @param decoys  **For SD-JWT issuance:** Num (fixed mode) or max num (random mode) of decoy digests to add for this hierarchical level. 0 if NONE, **for presentation:** _unused_.
         */
        @JsExport.Ignore
        fun createSDPayload(
            fullJWTClaimsSet: JWTClaimsSet,
            undisclosedJWTClaimsSet: JWTClaimsSet,
            decoyMode: DecoyMode = DecoyMode.NONE,
            decoys: Int = 0
        ) = createSDPayload(
            fullPayload = Json.parseToJsonElement(fullJWTClaimsSet.toString()).jsonObject,
            undisclosedPayload = Json.parseToJsonElement(undisclosedJWTClaimsSet.toString()).jsonObject,
            decoyMode = decoyMode,
            decoys = decoys
        )

        /**
         * Parse SD payload from JWT body and disclosure strings appended to JWT token.
         * Two distinct disclosures that hash to the same digest throw
         * [SDJwtVerificationException]; the caller is responsible for rejecting duplicate
         * disclosure strings before they collapse into [Set].
         *
         * @param jwtBody  Undisclosed JWT body payload
         * @param disclosures Encoded disclosure string, as appended to JWT token
         */
        fun parse(jwtBody: String, disclosures: Set<String>): SDPayload {
            val digestMap = mutableMapOf<String, SDisclosure>()
            disclosures.forEach { d ->
                val parsed = SDisclosure.parse(d)
                val digestStr = digest(d)
                if (digestMap.put(digestStr, parsed) != null) {
                    throw SDJwtVerificationException(
                        "Distinct disclosures hash to the same digest: $digestStr"
                    )
                }
            }
            return SDPayload(
                undisclosedPayload = Json.parseToJsonElement(jwtBody.decodeFromBase64Url().decodeToString()).jsonObject,
                digestedDisclosures = digestMap,
            )
        }
    }
}

/**
 * Returns the digest if [element] is a well-formed array-element wrapper
 * (`{"...": "<digest-string>"}`), `null` if it isn't a wrapper at all. Throws
 * [SDJwtVerificationException] when [element] looks like a wrapper (has the `...` key)
 * but is malformed (extra keys or non-string digest).
 */
internal fun arrayElementWrapperHash(element: JsonElement): String? {
    if (element !is JsonObject || !element.containsKey(SDPayload.ARRAY_ELEMENT_WRAPPER_KEY)) return null
    if (element.size != 1) {
        throw SDJwtVerificationException(
            "Array-element wrapper must have exactly one key, got ${element.size}"
        )
    }
    val v = element[SDPayload.ARRAY_ELEMENT_WRAPPER_KEY] as? JsonPrimitive
    if (v == null || !v.isString) {
        throw SDJwtVerificationException("Array-element wrapper digest must be a JSON string")
    }
    return v.content
}
