package id.walt.sdjwt

import dev.whyoleg.cryptography.random.CryptographyRandom
import id.walt.sdjwt.utils.SdjwtStringUtils.decodeFromBase64Url
import korlibs.crypto.SecureRandom
import korlibs.crypto.sha256
import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

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

    /**
     * Full payload, with all (selected) disclosures resolved recursively
     */
    val fullPayload
        get() = disclosePayloadRecursively(undisclosedPayload, null)

    /**
     * SDMap regenerated from undisclosed payload and disclosures.
     */
    val sdMap
        get() = SDMap.regenerateSDMap(undisclosedPayload, digestedDisclosures)

    private fun disclosePayloadRecursively(
        payload: JsonObject,
        verificationDisclosureMap: MutableMap<String, SDisclosure>?
    ): JsonObject {
        return buildJsonObject {
            payload.forEach { entry ->
                if (entry.key == SDJwt.DIGESTS_KEY) {
                    if (entry.value !is JsonArray) throw Exception("SD-JWT contains invalid ${SDJwt.DIGESTS_KEY} element")
                    entry.value.jsonArray.forEach {
                        unveilDisclosureIfPresent(
                            digest = it.jsonPrimitive.content,
                            objectBuilder = this,
                            verificationDisclosureMap = verificationDisclosureMap
                        )
                    }
                } else if (entry.value is JsonObject) {
                    put(
                        entry.key, disclosePayloadRecursively(
                            payload = entry.value.jsonObject,
                            verificationDisclosureMap = verificationDisclosureMap
                        )
                    )
                } else {
                    put(entry.key, entry.value)
                }
            }
        }
    }

    private fun unveilDisclosureIfPresent(
        digest: String,
        objectBuilder: JsonObjectBuilder,
        verificationDisclosureMap: MutableMap<String, SDisclosure>?
    ) {
        val sDisclosure = verificationDisclosureMap?.remove(digest) ?: digestedDisclosures[digest]
        if (sDisclosure != null) {
            objectBuilder.put(
                sDisclosure.key,
                if (sDisclosure.value is JsonObject) {
                    disclosePayloadRecursively(
                        payload = sDisclosure.value.jsonObject,
                        verificationDisclosureMap = verificationDisclosureMap
                    )
                } else sDisclosure.value
            )
        }
    }

    private fun filterDisclosures(currPayloadObject: JsonObject, sdMap: Map<String, SDField>): Set<String> {
        if (currPayloadObject.containsKey(SDJwt.DIGESTS_KEY) && currPayloadObject[SDJwt.DIGESTS_KEY] !is JsonArray) {
            throw Exception("Invalid ${SDJwt.DIGESTS_KEY} format found")
        }

        return currPayloadObject.filter { entry -> entry.value is JsonObject && !sdMap[entry.key]?.children.isNullOrEmpty() }
            .flatMap { entry ->
                filterDisclosures(entry.value.jsonObject, sdMap[entry.key]!!.children!!)
            }.plus(
                currPayloadObject[SDJwt.DIGESTS_KEY]?.jsonArray
                    ?.asSequence()
                    ?.map { it.jsonPrimitive.content }
                    ?.filter { digest -> digestedDisclosures.containsKey(digest) }
                    ?.map { digest -> digestedDisclosures[digest]!! }
                    ?.filter { sd -> sdMap[sd.key]?.sd == true }
                    ?.flatMap { sd ->
                        listOf(sd.disclosure).plus(
                            if (sd.value is JsonObject && !sdMap[sd.key]?.children.isNullOrEmpty()) {
                                filterDisclosures(sd.value, sdMap[sd.key]!!.children!!)
                            } else listOf()
                        )
                    }
                    ?.toList() ?: listOf()
            ).toSet()
    }

    /**
     * Payload with selectively disclosed fields and undisclosed fields filtered out.
     * @param sdMap Map indicating per field (recursively) whether they are selected for disclosure
     */
    fun withSelectiveDisclosures(sdMap: Map<String, SDField>): SDPayload {
        val selectedDisclosures = filterDisclosures(undisclosedPayload, sdMap)
        return SDPayload(
            undisclosedPayload = undisclosedPayload,
            digestedDisclosures = digestedDisclosures.filterValues { selectedDisclosures.contains(it.disclosure) }
        )
    }

    /**
     * Payload with all selectively dislosable fields filtered out (all fields undisclosed)
     */
    fun withoutDisclosures(): SDPayload {
        return SDPayload(undisclosedPayload, mapOf())
    }

    /**
     * Verify digests in JWT payload match with disclosures appended to JWT token.
     */
    fun verifyDisclosures() = digestedDisclosures.toMutableMap().also {
        disclosePayloadRecursively(undisclosedPayload, it)
    }.isEmpty()

    @OptIn(ExperimentalEncodingApi::class)
    @JsExport.Ignore // see SDPayloadBuilder for JS support
    companion object {

        private fun digest(value: String): String {
            val messageDigest = value.encodeToByteArray().sha256()
            return messageDigest.base64Url
        }

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
                SDisclosure(
                    disclosure = disclosure,
                    salt = salt,
                    key = key,
                    value = value
                )
            }
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
                sdMap[key]?.sd == true || !sdMap[key]?.children.isNullOrEmpty()
            }.map { entry ->
                if (entry.value !is JsonObject || sdMap[entry.key]?.children.isNullOrEmpty()) {
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
                sdPayload[SDJwt.DIGESTS_KEY] = buildJsonArray {
                    digests.forEach { add(it) }
                    if (sdMap.decoyMode != DecoyMode.NONE && sdMap.decoys > 0) {
                        val numDecoys = when (sdMap.decoyMode) {
                            // NOTE: SecureRandom.nextInt always returns 0! Use nextDouble instead
                            DecoyMode.RANDOM -> SecureRandom.nextDouble(1.0, sdMap.decoys + 1.0).toInt()
                            DecoyMode.FIXED -> sdMap.decoys
                        }
                        repeat(numDecoys) {
                            add(digest(generateSalt()))
                        }
                    }
                }
            }
            return JsonObject(sdPayload)
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
         * Parse SD payload from JWT body and disclosure strings appended to JWT token
         * @param jwtBody  Undisclosed JWT body payload
         * @param disclosures Encoded disclosure string, as appended to JWT token
         */
        fun parse(jwtBody: String, disclosures: Set<String>): SDPayload {
            return SDPayload(
                undisclosedPayload = Json.parseToJsonElement(jwtBody.decodeFromBase64Url().decodeToString()).jsonObject,
                digestedDisclosures = disclosures.associate { Pair(digest(it), SDisclosure.parse(it)) })
        }
    }
}
