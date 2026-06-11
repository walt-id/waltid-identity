package id.walt.sdjwt

import id.walt.sdjwt.utils.Base64Utils.encodeToBase64Url
import kotlinx.serialization.json.*
import org.kotlincrypto.hash.sha2.SHA256
import kotlin.test.*

/**
 * Tests for RFC 9901 array-element selective disclosure.
 *
 * Specification references use section numbers from RFC 9901.
 */
class SDArrayElementTest {

    // Disclosure parsing (RFC 9901 §4.2.1, §4.2.2, §4.2.3)

    /** RFC §4.2.2: `["lklxF5jMYlGTPUovMNIvCA", "FR"]`. */
    @Test
    fun parse_arrayElementDisclosure_FR() {
        val disclosure = "WyJsa2x4RjVqTVlsR1RQVW92TU5JdkNBIiwgIkZSIl0"
        val parsed = SDisclosure.parse(disclosure)
        assertIs<ArrayElementDisclosure>(parsed)
        assertEquals("lklxF5jMYlGTPUovMNIvCA", parsed.salt)
        assertEquals(JsonPrimitive("FR"), parsed.value)
        assertEquals(disclosure, parsed.disclosure)
    }

    /** RFC §5.1 example: SHA-256 of the "US" array-element disclosure. */
    @Test
    fun parse_arrayElementDisclosure_US_matchesRfc9901Example() {
        val disclosure = "WyJsa2x4RjVqTVlsR1RQVW92TU5JdkNBIiwgIlVTIl0"
        val parsed = SDisclosure.parse(disclosure)
        assertIs<ArrayElementDisclosure>(parsed)
        assertEquals(JsonPrimitive("US"), parsed.value)
        val hash = SHA256().digest(disclosure.encodeToByteArray()).encodeToBase64Url()
        assertEquals("pFndjkZ_VCzmyTa6UjlZo3dh-ko8aIKQc9DlGzhaVYo", hash)
    }

    /** RFC §5.1 example: SHA-256 of the "DE" array-element disclosure. */
    @Test
    fun parse_arrayElementDisclosure_DE_matchesRfc9901Example() {
        val disclosure = "WyJuUHVvUW5rUkZxM0JJZUFtN0FuWEZBIiwgIkRFIl0"
        val parsed = SDisclosure.parse(disclosure)
        assertIs<ArrayElementDisclosure>(parsed)
        assertEquals(JsonPrimitive("DE"), parsed.value)
        val hash = SHA256().digest(disclosure.encodeToByteArray()).encodeToBase64Url()
        assertEquals("7Cf6JkPudry3lcbwHgeZ8khAv1U1OSlerP0VkBJrWZ0", hash)
    }

    /** RFC §5.1: 3-element object-property disclosure continues to parse. */
    @Test
    fun parse_objectPropertyDisclosure_givenName() {
        val disclosure = "WyIyR0xDNDJzS1F2ZUNmR2ZyeU5STjl3IiwgImdpdmVuX25hbWUiLCAiSm9obiJd"
        val parsed = SDisclosure.parse(disclosure)
        assertIs<ObjectPropertyDisclosure>(parsed)
        assertEquals("2GLC42sKQveCfGfryNRN9w", parsed.salt)
        assertEquals("given_name", parsed.key)
        assertEquals(JsonPrimitive("John"), parsed.value)
    }

    /** RFC §7.1 step 3.c: anything but 2 or 3 elements MUST be rejected. */
    @Test
    fun parse_rejectsArrayOfSize1() {
        val badDisclosure = buildJsonArray { add("onlySalt") }.toString()
            .encodeToByteArray().encodeToBase64Url()
        assertFailsWith<SDJwtVerificationException> { SDisclosure.parse(badDisclosure) }
    }

    @Test
    fun parse_rejectsArrayOfSize4() {
        val badDisclosure = buildJsonArray {
            add("salt"); add("key"); add("value"); add("extra")
        }.toString().encodeToByteArray().encodeToBase64Url()
        assertFailsWith<SDJwtVerificationException> { SDisclosure.parse(badDisclosure) }
    }

    @Test
    fun parse_rejectsNonStringSalt() {
        val badDisclosure = buildJsonArray {
            add(42)
            add("key")
            add("value")
        }.toString().encodeToByteArray().encodeToBase64Url()
        assertFailsWith<SDJwtVerificationException> { SDisclosure.parse(badDisclosure) }
    }

    /** Round-trip via the compare-by-key overload must preserve array-element disclosures. */
    @Test
    fun generateSDMap_recoversArrayChildrenFromWireWrappers() {
        val fullPayload = buildJsonObject {
            put("name", "Alice")
            put("nationalities", buildJsonArray { add("US"); add("DE") })
        }
        val sourceMap = SDMap(
            mapOf(
                "name" to SDField(sd = true),
                "nationalities" to SDField(
                    sd = false,
                    arrayChildren = SDArray(elements = listOf(SDField(sd = true), SDField(sd = true))),
                ),
            )
        )
        val sdPayload = SDPayload.createSDPayload(fullPayload, sourceMap)

        val recovered = SDMap.generateSDMap(
            fullPayload = sdPayload.fullPayload,
            undisclosedPayload = sdPayload.undisclosedPayload,
        )
        val nationalities = recovered["nationalities"]
        assertNotNull(nationalities)
        val arr = nationalities.arrayChildren
        assertNotNull(arr)
        assertEquals(2, arr.elements.size)
        assertTrue(arr.elements.all { it.sd })
    }

    /** Bad base64url input must surface as [SDJwtVerificationException], not an upstream IAE. */
    @Test
    fun parse_rejectsInvalidBase64() {
        assertFailsWith<SDJwtVerificationException> { SDisclosure.parse("not!valid!base64") }
    }

    /** Valid base64 of non-JSON content must surface as [SDJwtVerificationException]. */
    @Test
    fun parse_rejectsValidBase64ButNotJson() {
        val notJson = "this is not json".encodeToByteArray().encodeToBase64Url()
        assertFailsWith<SDJwtVerificationException> { SDisclosure.parse(notJson) }
    }

    /** Valid JSON that isn't an array (e.g. an object) must surface as [SDJwtVerificationException]. */
    @Test
    fun parse_rejectsValidJsonButNotArray() {
        val notArray = """{"salt":"x","key":"y","value":1}""".encodeToByteArray().encodeToBase64Url()
        assertFailsWith<SDJwtVerificationException> { SDisclosure.parse(notArray) }
    }

    // Data-model construction

    @Test
    fun sdField_arrayChildren_carriesSDArray() {
        val field = SDField(
            sd = false,
            arrayChildren = SDArray(
                elements = listOf(SDField(sd = true), SDField(sd = false))
            )
        )
        val arr = field.arrayChildren
        assertNotNull(arr)
        assertEquals(2, arr.elements.size)
        assertTrue(arr.elements[0].sd)
        assertFalse(arr.elements[1].sd)
        assertNull(field.children)
    }

    @Test
    fun sdField_children_carriesSDMap() {
        val field = SDField(
            sd = true,
            children = SDMap(mapOf("x" to SDField(sd = true)))
        )
        val children = field.children
        assertNotNull(children)
        assertTrue(children["x"]!!.sd)
        assertNull(field.arrayChildren)
    }

    @Test
    fun sdField_rejectsBothChildrenSet() {
        assertFails {
            SDField(
                sd = true,
                children = SDMap(mapOf()),
                arrayChildren = SDArray(),
            )
        }
    }

    /** Constructor must reject negative decoy counts up front, not later in `repeat(-1)`. */
    @Test
    fun sdArray_rejectsNegativeDecoys() {
        assertFails { SDArray(decoyMode = DecoyMode.FIXED, decoys = -1) }
    }

    @Test
    fun sdArray_wildcard_appliesToAllIndices() {
        val arr = SDArray(elements = emptyList(), wildcard = SDField(sd = true))
        assertEquals(0, arr.elements.size)
        assertNotNull(arr.wildcard)
        assertTrue(arr.wildcard.sd)
    }

    @Test
    fun sdArray_toJson_roundTrips() {
        val original = SDArray(
            elements = listOf(SDField(sd = true), SDField(sd = false)),
            wildcard = SDField(sd = true),
            decoyMode = DecoyMode.FIXED,
            decoys = 3,
        )
        val json = original.toJSON()
        val restored = SDArray.fromJSON(json)
        assertEquals(original.elements.size, restored.elements.size)
        assertEquals(original.elements[0].sd, restored.elements[0].sd)
        assertEquals(original.elements[1].sd, restored.elements[1].sd)
        assertEquals(true, restored.wildcard?.sd)
        assertEquals(DecoyMode.FIXED, restored.decoyMode)
        assertEquals(3, restored.decoys)
    }

    @Test
    fun sdMap_toJson_roundTrips_regressionGuard() {
        val original = SDMap(
            mapOf(
                "a" to SDField(sd = true),
                "b" to SDField(sd = false, children = SDMap(mapOf("c" to SDField(sd = true))))
            ),
            decoyMode = DecoyMode.RANDOM,
            decoys = 2,
        )
        val restored = SDMap.fromJSON(original.toJSON())
        assertTrue(restored["a"]!!.sd)
        assertFalse(restored["b"]!!.sd)
        val nested = restored["b"]!!.children
        assertIs<SDMap>(nested)
        assertTrue(nested["c"]!!.sd)
        assertEquals(DecoyMode.RANDOM, restored.decoyMode)
        assertEquals(2, restored.decoys)
    }

    @Test
    fun sdMap_regenerationDescribesArrayDisclosures() {
        val fullPayload = buildJsonObject {
            put("nationalities", buildJsonArray { add("US"); add("DE") })
        }
        val sdMap = SDMap(
            mapOf(
                "nationalities" to SDField(
                    sd = false,
                    arrayChildren = SDArray(elements = listOf(SDField(sd = true), SDField(sd = false)))
                )
            )
        )
        val sdPayload = SDPayload.createSDPayload(fullPayload, sdMap)

        val regenerated = sdPayload.sdMap
        val arr = regenerated["nationalities"]?.arrayChildren
        assertNotNull(arr)
        assertEquals(2, arr.elements.size)
        assertTrue(arr.elements[0].sd, "index 0 was wrapped, regenerated descriptor must mark it SD")
        assertFalse(arr.elements[1].sd, "index 1 was passed through, regenerated descriptor must mark it not-SD")
    }

    // Issuance: generation

    @Test
    fun generation_twoNationalities_bothSD_producesTwoWrappersAtSamePositions() {
        val fullPayload = buildJsonObject {
            put("nationalities", buildJsonArray { add("US"); add("DE") })
        }
        val sdMap = SDMap(
            mapOf(
                "nationalities" to SDField(
                    sd = false,
                    arrayChildren = SDArray(elements = listOf(SDField(sd = true), SDField(sd = true)))
                )
            )
        )
        val sdPayload = SDPayload.createSDPayload(fullPayload, sdMap)

        // Undisclosed payload still exposes nationalities as an array with 2 wrapper entries
        val nationalities = sdPayload.undisclosedPayload["nationalities"]!!.jsonArray
        assertEquals(2, nationalities.size)
        nationalities.forEach {
            val obj = it.jsonObject
            assertEquals(1, obj.size)
            assertTrue(obj.containsKey("..."))
            assertIs<JsonPrimitive>(obj["..."])
        }

        // Two disclosures produced, both are array-element (2-element) disclosures
        assertEquals(2, sdPayload.sDisclosures.size)
        sdPayload.sDisclosures.forEach { assertIs<ArrayElementDisclosure>(it) }

        // Wrapper hashes match the keys of the disclosure map.
        val wrapperHashes = nationalities.map { it.jsonObject["..."]!!.jsonPrimitive.content }.toSet()
        assertEquals(wrapperHashes, sdPayload.digestedDisclosures.keys)
    }

    @Test
    fun generation_mixedSdNonSdElements_preservesOrderAndPositions() {
        val fullPayload = buildJsonObject {
            put("colors", buildJsonArray { add("red"); add("green"); add("blue") })
        }
        val sdMap = SDMap(
            mapOf(
                "colors" to SDField(
                    sd = false,
                    arrayChildren = SDArray(
                        elements = listOf(
                            SDField(sd = false),
                            SDField(sd = true),
                            SDField(sd = false),
                        )
                    )
                )
            )
        )
        val sdPayload = SDPayload.createSDPayload(fullPayload, sdMap)

        val colors = sdPayload.undisclosedPayload["colors"]!!.jsonArray
        assertEquals(3, colors.size)
        assertEquals(JsonPrimitive("red"), colors[0])
        assertTrue(colors[1].jsonObject.containsKey("..."))
        assertEquals(JsonPrimitive("blue"), colors[2])

        assertEquals(1, sdPayload.sDisclosures.size)
        val disclosure = sdPayload.sDisclosures.single()
        assertIs<ArrayElementDisclosure>(disclosure)
        assertEquals(JsonPrimitive("green"), disclosure.value)
    }

    @Test
    fun generation_arrayOfObjects_perIndexPropertySD() {
        val fullPayload = buildJsonObject {
            put("people", buildJsonArray {
                add(buildJsonObject { put("name", "Alice"); put("age", 30) })
            })
        }
        val sdMap = SDMap(
            mapOf(
                "people" to SDField(
                    sd = false,
                    arrayChildren = SDArray(
                        elements = listOf(
                            SDField(
                                sd = false,
                                children = SDMap(mapOf("name" to SDField(sd = true)))
                            )
                        )
                    )
                )
            )
        )
        val sdPayload = SDPayload.createSDPayload(fullPayload, sdMap)

        val person0 = sdPayload.undisclosedPayload["people"]!!.jsonArray[0].jsonObject
        assertFalse(person0.containsKey("name"))
        assertEquals(JsonPrimitive(30), person0["age"])
        assertTrue(person0.containsKey(SDJwt.DIGESTS_KEY))
    }

    @Test
    fun generation_arrayOfArrays_nestedIndexSD() {
        val fullPayload = buildJsonObject {
            put("m", buildJsonArray {
                add(buildJsonArray { add("a"); add("b") })
                add(buildJsonArray { add("c"); add("d") })
            })
        }
        val sdMap = SDMap(
            mapOf(
                "m" to SDField(
                    sd = false,
                    arrayChildren = SDArray(
                        elements = listOf(
                            SDField(
                                sd = false,
                                arrayChildren = SDArray(
                                    elements = listOf(SDField(sd = false), SDField(sd = true))
                                )
                            ),
                            SDField(sd = false),
                        )
                    )
                )
            )
        )
        val sdPayload = SDPayload.createSDPayload(fullPayload, sdMap)

        val inner0 = sdPayload.undisclosedPayload["m"]!!.jsonArray[0].jsonArray
        assertEquals(2, inner0.size)
        assertEquals(JsonPrimitive("a"), inner0[0])
        assertTrue(inner0[1].jsonObject.containsKey("..."))

        assertEquals(1, sdPayload.sDisclosures.size)
        val d = sdPayload.sDisclosures.single()
        assertIs<ArrayElementDisclosure>(d)
        assertEquals(JsonPrimitive("b"), d.value)
    }

    @Test
    fun generation_arrayLevelDecoys_FIXED_addsDecoyWrappers() {
        val fullPayload = buildJsonObject {
            put("xs", buildJsonArray { add("a") })
        }
        val sdMap = SDMap(
            mapOf(
                "xs" to SDField(
                    sd = false,
                    arrayChildren = SDArray(
                        elements = listOf(SDField(sd = true)),
                        decoyMode = DecoyMode.FIXED,
                        decoys = 3,
                    )
                )
            )
        )
        val sdPayload = SDPayload.createSDPayload(fullPayload, sdMap)

        val xs = sdPayload.undisclosedPayload["xs"]!!.jsonArray
        // 1 real + 3 decoy
        assertEquals(4, xs.size)
        xs.forEach { el ->
            val obj = el.jsonObject
            assertEquals(1, obj.size)
            assertTrue(obj.containsKey("..."))
        }

        // Only 1 disclosure — decoys have no corresponding disclosure
        assertEquals(1, sdPayload.sDisclosures.size)
    }

    @Test
    fun generation_arrayLevelDecoys_RANDOM_addsUpToMax() {
        val fullPayload = buildJsonObject {
            put("xs", buildJsonArray { add("a"); add("b") })
        }
        val sdMap = SDMap(
            mapOf(
                "xs" to SDField(
                    sd = false,
                    arrayChildren = SDArray(
                        elements = listOf(SDField(sd = true), SDField(sd = true)),
                        decoyMode = DecoyMode.RANDOM,
                        decoys = 5,
                    )
                )
            )
        )
        val sdPayload = SDPayload.createSDPayload(fullPayload, sdMap)

        val xs = sdPayload.undisclosedPayload["xs"]!!.jsonArray
        assertTrue(xs.size in 2..7, "expected 2 real + [0..5] decoys, got ${xs.size}")
        assertEquals(2, sdPayload.sDisclosures.size)
    }

    /**
     * RFC §4.2.5: decoys exist to obscure the original element count, so they MUST NOT be
     * trivially distinguishable from real elements. With plain (non-SD) real elements, the
     * decoys must be interleaved randomly while real elements preserve their relative order.
     */
    @Test
    fun generation_arrayDecoys_areInterleavedAndPreserveRealRelativeOrder() {
        val fullPayload = buildJsonObject {
            put("xs", buildJsonArray { add("alpha"); add("beta"); add("gamma") })
        }
        val sdMap = SDMap(
            mapOf(
                "xs" to SDField(
                    sd = false,
                    arrayChildren = SDArray(
                        elements = listOf(SDField(sd = false), SDField(sd = false), SDField(sd = false)),
                        decoyMode = DecoyMode.FIXED,
                        decoys = 4,
                    )
                )
            )
        )
        val sdPayload = SDPayload.createSDPayload(fullPayload, sdMap)
        val xs = sdPayload.undisclosedPayload["xs"]!!.jsonArray

        assertEquals(7, xs.size)
        assertEquals(0, sdPayload.sDisclosures.size)

        val plainPositions = xs.mapIndexedNotNull { i, el ->
            (el as? JsonPrimitive)?.takeIf { it.isString }?.content?.let { i to it }
        }
        assertEquals(listOf("alpha", "beta", "gamma"), plainPositions.map { it.second })
        val decoyCount = xs.count { el ->
            (el as? JsonObject)?.let { it.size == 1 && it.containsKey("...") } == true
        }
        assertEquals(4, decoyCount)
    }

    /**
     * RFC §4.2.4.1: a claim name MUST NOT appear both as a disclosed claim (referenced from
     * `_sd`) and as a plain claim. `fullPayload` rejects such payloads.
     */
    @Test
    fun verify_rejectsCollisionBetweenDisclosedAndPlain() {
        val fullPayload = buildJsonObject { put("name", "Alice") }
        val sdPayload = SDPayload.createSDPayload(fullPayload, SDMap(mapOf("name" to SDField(sd = true))))
        val collidedUndisclosed = JsonObject(
            sdPayload.undisclosedPayload + mapOf("name" to JsonPrimitive("Bob"))
        )
        val collided = SDPayload.parse(
            jwtBody = collidedUndisclosed.toString().encodeToByteArray().encodeToBase64Url(),
            disclosures = sdPayload.sDisclosures.map { it.disclosure }.toSet()
        )
        assertFailsWith<SDJwtVerificationException> { collided.fullPayload }
    }

    // Verification: happy paths (RFC §7.1)

    @Test
    fun verify_rfcExample_bothNationalitiesDisclosed() {
        val sdJwt = SDJwt.parse(RFC_5_1_SD_JWT)
        val processed = sdJwt.sdPayload.fullPayload
        val nationalities = processed["nationalities"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("US", "DE"), nationalities)
    }

    @Test
    fun verify_rfcExample_onlyUSDisclosed_omitsMissingElement() {
        // Drop the DE disclosure from the tilde chain (RFC §7.1 step 3.d).
        val stripped = RFC_5_1_SD_JWT.replace("~WyJuUHVvUW5rUkZxM0JJZUFtN0FuWEZBIiwgIkRFIl0", "")
        val sdJwt = SDJwt.parse(stripped)
        val processed = sdJwt.sdPayload.fullPayload
        val nationalities = processed["nationalities"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("US"), nationalities, "undisclosed element must be removed")
    }

    @Test
    fun verify_rfcExample_neitherDisclosed_yieldsEmptyArray() {
        val stripped = RFC_5_1_SD_JWT
            .replace("~WyJsa2x4RjVqTVlsR1RQVW92TU5JdkNBIiwgIlVTIl0", "")
            .replace("~WyJuUHVvUW5rUkZxM0JJZUFtN0FuWEZBIiwgIkRFIl0", "")
        val sdJwt = SDJwt.parse(stripped)
        val processed = sdJwt.sdPayload.fullPayload
        assertEquals(0, processed["nationalities"]!!.jsonArray.size)
    }

    // Verification: normative rejects (RFC 9901 §7.1). fullPayload throws
    // SDJwtVerificationException for malformed wrappers, wrong-arity disclosures
    // for the surrounding context, forbidden claim names, name collisions,
    // duplicate digest references, and unreferenced disclosures.

    /** RFC §4.2.4.2: "There MUST NOT be any other keys in the object." */
    @Test
    fun verify_rejectsArrayWrapperWithExtraKeys() {
        val payload = buildJsonObject {
            put("arr", buildJsonArray {
                add(buildJsonObject {
                    put("...", "pFndjkZ_VCzmyTa6UjlZo3dh-ko8aIKQc9DlGzhaVYo")
                    put("extra", 1)
                })
            })
        }
        val sdPayload = SDPayload(
            undisclosedPayload = payload,
            digestedDisclosures = mapOf(
                "pFndjkZ_VCzmyTa6UjlZo3dh-ko8aIKQc9DlGzhaVYo" to
                        SDisclosure.parse("WyJsa2x4RjVqTVlsR1RQVW92TU5JdkNBIiwgIlVTIl0")
            )
        )
        assertFailsWith<SDJwtVerificationException> { sdPayload.fullPayload }
    }

    /** RFC §7.1 3.c.ii.1: object context requires a 3-element disclosure. */
    @Test
    fun verify_rejects2ElementDisclosureInObjectContext() {
        val disclosure = "WyJsa2x4RjVqTVlsR1RQVW92TU5JdkNBIiwgIlVTIl0"
        val hash = SHA256().digest(disclosure.encodeToByteArray()).encodeToBase64Url()
        val payload = buildJsonObject {
            put(SDJwt.DIGESTS_KEY, buildJsonArray { add(hash) })
        }
        val sdPayload = SDPayload(
            undisclosedPayload = payload,
            digestedDisclosures = mapOf(hash to SDisclosure.parse(disclosure))
        )
        assertFailsWith<SDJwtVerificationException> { sdPayload.fullPayload }
    }

    /** RFC §7.1 3.c.iii.1: array context requires a 2-element disclosure. */
    @Test
    fun verify_rejects3ElementDisclosureInArrayContext() {
        val disclosure = "WyIyR0xDNDJzS1F2ZUNmR2ZyeU5STjl3IiwgImdpdmVuX25hbWUiLCAiSm9obiJd"
        val hash = SHA256().digest(disclosure.encodeToByteArray()).encodeToBase64Url()
        val payload = buildJsonObject {
            put("arr", buildJsonArray {
                add(buildJsonObject { put("...", hash) })
            })
        }
        val sdPayload = SDPayload(
            undisclosedPayload = payload,
            digestedDisclosures = mapOf(hash to SDisclosure.parse(disclosure))
        )
        assertFailsWith<SDJwtVerificationException> { sdPayload.fullPayload }
    }

    /** RFC §7.1 step 3.c.ii.2: claim name `_sd` or `...` → reject. */
    @Test
    fun verify_rejectsObjectPropertyDisclosureWithClaimNameThreeDots() {
        val malformed = buildJsonArray {
            add("saltsaltsaltsaltsaltsa")
            add("...")
            add("value")
        }.toString().encodeToByteArray().encodeToBase64Url()
        val hash = SHA256().digest(malformed.encodeToByteArray()).encodeToBase64Url()
        val payload = buildJsonObject {
            put(SDJwt.DIGESTS_KEY, buildJsonArray { add(hash) })
        }
        val sdPayload = SDPayload(
            undisclosedPayload = payload,
            digestedDisclosures = mapOf(hash to SDisclosure.parse(malformed))
        )
        assertFailsWith<SDJwtVerificationException> { sdPayload.fullPayload }
    }

    @Test
    fun verify_rejectsObjectPropertyDisclosureWithClaimName_sd() {
        val malformed = buildJsonArray {
            add("saltsaltsaltsaltsaltsa")
            add("_sd")
            add("value")
        }.toString().encodeToByteArray().encodeToBase64Url()
        val hash = SHA256().digest(malformed.encodeToByteArray()).encodeToBase64Url()
        val payload = buildJsonObject {
            put(SDJwt.DIGESTS_KEY, buildJsonArray { add(hash) })
        }
        val sdPayload = SDPayload(
            undisclosedPayload = payload,
            digestedDisclosures = mapOf(hash to SDisclosure.parse(malformed))
        )
        assertFailsWith<SDJwtVerificationException> { sdPayload.fullPayload }
    }

    /** RFC §7.1 step 4: a hash encountered more than once MUST be rejected. */
    @Test
    fun verify_rejectsDuplicateHashReferences() {
        val disclosure = "WyJsa2x4RjVqTVlsR1RQVW92TU5JdkNBIiwgIlVTIl0"
        val hash = "pFndjkZ_VCzmyTa6UjlZo3dh-ko8aIKQc9DlGzhaVYo"
        val payload = buildJsonObject {
            put("arr", buildJsonArray {
                add(buildJsonObject { put("...", hash) })
                add(buildJsonObject { put("...", hash) })
            })
        }
        val sdPayload = SDPayload(
            undisclosedPayload = payload,
            digestedDisclosures = mapOf(hash to SDisclosure.parse(disclosure))
        )
        assertFailsWith<SDJwtVerificationException> { sdPayload.fullPayload }
    }

    /**
     * RFC §4.2.4: every entry of `_sd` MUST be a JSON string. A numeric or boolean entry
     * must be rejected (object-context counterpart of the wrapper isString check).
     */
    @Test
    fun verify_rejectsNonStringSdEntry() {
        val payload = buildJsonObject {
            put(SDJwt.DIGESTS_KEY, buildJsonArray { add(JsonPrimitive(42)) })
        }
        val sdPayload = SDPayload(
            undisclosedPayload = payload,
            digestedDisclosures = emptyMap(),
        )
        assertFailsWith<SDJwtVerificationException> { sdPayload.fullPayload }
    }

    /** RFC §4.2.4: `_sd` MUST be a JSON array. */
    @Test
    fun verify_rejectsNonArraySdValue() {
        val payload = buildJsonObject {
            put(SDJwt.DIGESTS_KEY, JsonPrimitive("not-an-array"))
        }
        val sdPayload = SDPayload(undisclosedPayload = payload, digestedDisclosures = emptyMap())
        assertFailsWith<SDJwtVerificationException> { sdPayload.fullPayload }
    }

    /** RFC §4.2.4.2: array-element wrapper digest MUST be a JSON string. */
    @Test
    fun verify_rejectsWrapperWithNonStringDigest() {
        val payload = buildJsonObject {
            put("arr", buildJsonArray {
                add(buildJsonObject { put("...", JsonPrimitive(42)) })
            })
        }
        val sdPayload = SDPayload(undisclosedPayload = payload, digestedDisclosures = emptyMap())
        assertFailsWith<SDJwtVerificationException> { sdPayload.fullPayload }
    }

    /**
     * RFC §7.1 step 3.c.ii.3: two disclosures referenced from the same `_sd` array sharing
     * the same claim name MUST be rejected.
     */
    @Test
    fun verify_rejectsDisclosedDisclosedCollision() {
        // Build two distinct ObjectPropertyDisclosures with the same key, both referenced.
        val d1 = "WyIyR0xDNDJzS1F2ZUNmR2ZyeU5STjl3IiwgIm5hbWUiLCAiQWxpY2UiXQ"
        val d2 = "WyJlbHVWNU9nM2dTTklJOEVZbnN4QV9BIiwgIm5hbWUiLCAiQm9iIl0"
        val h1 = SHA256().digest(d1.encodeToByteArray()).encodeToBase64Url()
        val h2 = SHA256().digest(d2.encodeToByteArray()).encodeToBase64Url()
        val payload = buildJsonObject {
            put(SDJwt.DIGESTS_KEY, buildJsonArray { add(h1); add(h2) })
        }
        val sdPayload = SDPayload(
            undisclosedPayload = payload,
            digestedDisclosures = mapOf(h1 to SDisclosure.parse(d1), h2 to SDisclosure.parse(d2)),
        )
        assertFailsWith<SDJwtVerificationException> { sdPayload.fullPayload }
    }

    /** RFC §4.2.4.1: `...` MUST NOT appear as a regular object claim name. */
    @Test
    fun verify_rejectsThreeDotsAsPlainClaimName() {
        val payload = buildJsonObject {
            put("...", JsonPrimitive("some value"))
        }
        val sdPayload = SDPayload(
            undisclosedPayload = payload,
            digestedDisclosures = emptyMap(),
        )
        assertFailsWith<SDJwtVerificationException> { sdPayload.fullPayload }
    }

    /** RFC §7.1 step 4: a disclosure repeated in the tilde chain MUST be rejected. */
    @Test
    fun parse_rejectsDuplicateDisclosureInTildeChain() {
        val d = "WyJsa2x4RjVqTVlsR1RQVW92TU5JdkNBIiwgIlVTIl0"
        // Build a tilde-chain SD-JWT with the same disclosure listed twice.
        val header = "eyJhbGciOiJub25lIn0"
        val body = "{}".encodeToByteArray().encodeToBase64Url()
        val signature = "sig"
        val sdJwt = "$header.$body.$signature~$d~$d~"
        assertFailsWith<SDJwtVerificationException> { SDJwt.parse(sdJwt) }
    }

    /**
     * Bug guard: `nationalities.[0]` (logical index) must select the first **real** wrapper
     * even when decoys are interleaved at random wire positions. With the previous wire-index
     * impl, decoys at slot 0 caused the path DSL to silently miss the real index 0.
     */
    @Test
    fun presentation_pathDslIndexAlignsWithLogicalPositionAcrossDecoys() {
        val fullPayload = buildJsonObject {
            put("nationalities", buildJsonArray { add("US"); add("DE"); add("FR") })
        }
        val issuedMap = SDMap(
            mapOf(
                "nationalities" to SDField(
                    sd = false,
                    arrayChildren = SDArray(
                        elements = listOf(SDField(sd = true), SDField(sd = true), SDField(sd = true)),
                        decoyMode = DecoyMode.FIXED,
                        decoys = 4,
                    )
                )
            )
        )
        val presentationMap = SDMap.generateSDMap(listOf("nationalities.[0]"))

        // Run a few iterations so randomized decoy positions exercise the alignment.
        repeat(10) {
            val issued = SDPayload.createSDPayload(fullPayload, issuedMap)
            val presented = issued.withSelectiveDisclosures(presentationMap)
            val processed = presented.fullPayload
            val nationalities = processed["nationalities"]!!.jsonArray
                .mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
            assertEquals(listOf("US"), nationalities, "logical index 0 must always disclose 'US'")
        }
    }

    /** RFC §7.1 step 5: unreferenced disclosure MUST be rejected. */
    @Test
    fun verify_rejectsUnreferencedDisclosure() {
        val a = "WyJsa2x4RjVqTVlsR1RQVW92TU5JdkNBIiwgIlVTIl0"
        val b = "WyJuUHVvUW5rUkZxM0JJZUFtN0FuWEZBIiwgIkRFIl0"
        val hashA = SHA256().digest(a.encodeToByteArray()).encodeToBase64Url()
        val hashB = SHA256().digest(b.encodeToByteArray()).encodeToBase64Url()
        val payload = buildJsonObject {
            put("arr", buildJsonArray {
                add(buildJsonObject { put("...", hashA) })
            })
        }
        val sdPayload = SDPayload(
            undisclosedPayload = payload,
            digestedDisclosures = mapOf(
                hashA to SDisclosure.parse(a),
                hashB to SDisclosure.parse(b),     // no corresponding reference in payload
            )
        )
        assertFailsWith<SDJwtVerificationException> { sdPayload.fullPayload }
    }

    // Recursive disclosure (RFC §4.2.6)

    @Test
    fun recursiveDisclosure_outerArrayAndInnerElementsAllSD() {
        val fullPayload = buildJsonObject {
            put("family_name", "Möbius")
            put("nationalities", buildJsonArray { add("DE"); add("FR"); add("UK") })
        }
        val sdMap = SDMap(
            mapOf(
                "family_name" to SDField(sd = true),
                "nationalities" to SDField(
                    sd = true, // outer whole-array disclosure
                    arrayChildren = SDArray(
                        elements = listOf(SDField(sd = true), SDField(sd = true), SDField(sd = true))
                    )
                )
            )
        )
        val sdPayload = SDPayload.createSDPayload(fullPayload, sdMap)

        // Round-trip: full disclosure yields original payload.
        val processed = sdPayload.fullPayload
        assertEquals("Möbius", processed["family_name"]!!.jsonPrimitive.content)
        val nats = processed["nationalities"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("DE", "FR", "UK"), nats)
    }

    /**
     * RFC §7.1 step 5: stripping the outer object-property disclosure leaves the inner
     * array-element disclosures unreferenced — the SD-JWT MUST be rejected.
     */
    @Test
    fun recursiveDisclosure_strictModeRejectsUnreferencedInnerElements() {
        val fullPayload = buildJsonObject {
            put("family_name", "Möbius")
            put("nationalities", buildJsonArray { add("DE"); add("FR"); add("UK") })
        }
        val sdMap = SDMap(
            mapOf(
                "family_name" to SDField(sd = true),
                "nationalities" to SDField(
                    sd = true,
                    arrayChildren = SDArray(
                        elements = listOf(SDField(sd = true), SDField(sd = true), SDField(sd = true))
                    )
                )
            )
        )
        val full = SDPayload.createSDPayload(fullPayload, sdMap)
        val filtered = full.digestedDisclosures.filterValues {
            !(it is ObjectPropertyDisclosure && it.key == "nationalities")
        }
        val broken = SDPayload(
            undisclosedPayload = full.undisclosedPayload,
            digestedDisclosures = filtered,
        )
        assertFailsWith<SDJwtVerificationException> { broken.fullPayload }
    }

    // Presentation (holder selects which array elements to disclose)

    /**
     * The holder filter must enforce the same strict rules as `fullPayload` — same SD-JWT
     * cannot pass `withSelectiveDisclosures` and then fail verification.
     */
    @Test
    fun presentation_withSelectiveDisclosuresRejectsMalformedSdJwt() {
        val malformedPayload = buildJsonObject {
            put("arr", buildJsonArray {
                add(buildJsonObject {
                    put("...", "pFndjkZ_VCzmyTa6UjlZo3dh-ko8aIKQc9DlGzhaVYo")
                    put("extra", 1) // wrapper extra-key — must be rejected
                })
            })
        }
        val sdPayload = SDPayload(
            undisclosedPayload = malformedPayload,
            digestedDisclosures = mapOf(
                "pFndjkZ_VCzmyTa6UjlZo3dh-ko8aIKQc9DlGzhaVYo" to
                        SDisclosure.parse("WyJsa2x4RjVqTVlsR1RQVW92TU5JdkNBIiwgIlVTIl0")
            ),
        )
        val presentationMap = SDMap(mapOf("arr" to SDField(sd = false)))
        assertFailsWith<SDJwtVerificationException> { sdPayload.withSelectiveDisclosures(presentationMap) }
    }

    @Test
    fun presentation_holderWithholdsOneArrayElement() {
        val fullPayload = buildJsonObject {
            put("nationalities", buildJsonArray { add("US"); add("DE") })
        }
        val issuedMap = SDMap(
            mapOf(
                "nationalities" to SDField(
                    sd = false,
                    arrayChildren = SDArray(elements = listOf(SDField(sd = true), SDField(sd = true)))
                )
            )
        )
        val issued = SDPayload.createSDPayload(fullPayload, issuedMap)

        // Presentation map: disclose index 0, withhold index 1.
        val presentationMap = SDMap(
            mapOf(
                "nationalities" to SDField(
                    sd = false,
                    arrayChildren = SDArray(elements = listOf(SDField(sd = true), SDField(sd = false)))
                )
            )
        )
        val presented = issued.withSelectiveDisclosures(presentationMap)

        val processed = presented.fullPayload
        val nationalities = processed["nationalities"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("US"), nationalities)
    }

    // Path syntax for building an SDMap from dotted paths with array index / wildcard segments

    @Test
    fun pathSyntax_specificIndex() {
        val sdmap = SDMap.generateSDMap(listOf("nationalities.[0]"))
        val arr = sdmap["nationalities"]?.arrayChildren
        assertNotNull(arr)
        assertTrue(arr.elements[0].sd)
    }

    @Test
    fun pathSyntax_wildcard() {
        val sdmap = SDMap.generateSDMap(listOf("colors.[]"))
        val arr = sdmap["colors"]?.arrayChildren
        assertNotNull(arr)
        assertEquals(true, arr.wildcard?.sd)
    }

    @Test
    fun pathSyntax_propertyUnderWildcard() {
        val sdmap = SDMap.generateSDMap(listOf("cars.[].make"))
        val cars = sdmap["cars"]?.arrayChildren
        assertNotNull(cars)
        val wildcardChildren = cars.wildcard?.children
        assertNotNull(wildcardChildren)
        assertTrue(wildcardChildren["make"]!!.sd)
    }

    @Test
    fun pathSyntax_nestedArrayOfArrays() {
        val sdmap = SDMap.generateSDMap(listOf("contacts.[0].[2]"))
        val contacts = sdmap["contacts"]?.arrayChildren
        assertNotNull(contacts)
        val inner = contacts.elements[0].arrayChildren
        assertNotNull(inner)
        assertTrue(inner.elements[2].sd)
    }

    /**
     * Mixing an explicit index past 0 with a wildcard: indices not explicitly listed (0, 1)
     * should inherit the wildcard's settings instead of being silently overridden by
     * non-disclosable placeholders.
     */
    @Test
    fun pathSyntax_explicitIndexAndWildcard_indicesInheritWildcard() {
        val sdmap = SDMap.generateSDMap(listOf("arr.[2]", "arr.[]"))
        val arr = sdmap["arr"]?.arrayChildren
        assertNotNull(arr)
        // Wildcard is set …
        assertEquals(true, arr.wildcard?.sd)
        // … and indices 0/1 (not explicitly listed) should be marked sd = true via wildcard,
        // not the default `SDField(false)` placeholder.
        assertTrue(arr.elements[0].sd, "index 0 should inherit from wildcard")
        assertTrue(arr.elements[1].sd, "index 1 should inherit from wildcard")
        assertTrue(arr.elements[2].sd, "index 2 was explicitly listed")
    }

    /**
     * `[0].city + [].zip` means "disclose city at index 0 AND zip at every index". Index 0
     * must inherit the wildcard's `zip` rule on top of its own `city`.
     */
    @Test
    fun pathSyntax_explicitIndexAndWildcardAtSameLevel_mergesBoth() {
        val sdmap = SDMap.generateSDMap(listOf("addresses.[0].city", "addresses.[].zip"))
        val arr = sdmap["addresses"]?.arrayChildren
        assertNotNull(arr)
        val zero = arr.elements[0].children
        assertNotNull(zero)
        assertEquals(true, zero["city"]?.sd, "city must be disclosable at [0]")
        assertEquals(true, zero["zip"]?.sd, "zip must be disclosable at [0] via wildcard merge")
        // Wildcard still serves indices > maxIndex.
        assertEquals(true, arr.wildcard?.children?.get("zip")?.sd)
    }

    @Test
    fun pathSyntax_malformedSegment_throws() {
        assertFails { SDMap.generateSDMap(listOf("a.[]x")) }
        assertFails { SDMap.generateSDMap(listOf("a.[-1]")) }
        assertFails { SDMap.generateSDMap(listOf("a.[abc]")) }
    }

    // Test helpers and fixtures

    companion object {
        /**
         * Full SD-JWT from the RFC 9901 §5.1 example, with all ten disclosures:
         * eight object-property disclosures and two array-element disclosures
         * (for the `nationalities` array).
         */
        private const val RFC_5_1_SD_JWT =
            "eyJhbGciOiAiRVMyNTYiLCAidHlwIjogImV4YW1wbGUrc2Qtand0In0." +
            "eyJfc2QiOiBbIkNyUWU3UzVrcUJBSHQtbk1ZWGdjNmJkdDJTSDVhVFkxc1VfTS1QZ2tqUEkiLCAi" +
            "SnpZakg0c3ZsaUgwUjNQeUVNZmVadTZKdDY5dTVxZWhabzdGN0VQWWxTRSIsICJQb3JGYnBLdVZ1" +
            "Nnh5bUphZ3ZrRnNGWEFiUm9jMkpHbEFVQTJCQTRvN2NJIiwgIlRHZjRvTGJnd2Q1SlFhSHlLVlFa" +
            "VTlVZEdFMHc1cnREc3JaemZVYW9tTG8iLCAiWFFfM2tQS3QxWHlYN0tBTmtxVlI2eVoyVmE1TnJQ" +
            "SXZQWWJ5TXZSS0JNTSIsICJYekZyendzY002R242Q0pEYzZ2Vks4QmtNbmZHOHZPU0tmcFBJWmRB" +
            "ZmRFIiwgImdiT3NJNEVkcTJ4Mkt3LXc1d1BFemFrb2I5aFYxY1JEMEFUTjNvUUw5Sk0iLCAianN1" +
            "OXlWdWx3UVFsaEZsTV8zSmx6TWFTRnpnbGhRRzBEcGZheVF3TFVLNCJdLCAiaXNzIjogImh0dHBz" +
            "Oi8vaXNzdWVyLmV4YW1wbGUuY29tIiwgImlhdCI6IDE2ODMwMDAwMDAsICJleHAiOiAxODgzMDAw" +
            "MDAwLCAic3ViIjogInVzZXJfNDIiLCAibmF0aW9uYWxpdGllcyI6IFt7Ii4uLiI6ICJwRm5kamta" +
            "X1ZDem15VGE2VWpsWm8zZGgta284YUlLUWM5RGxHemhhVllvIn0sIHsiLi4uIjogIjdDZjZKa1B1" +
            "ZHJ5M2xjYndIZ2VaOGtoQXYxVTFPU2xlclAwVmtCSnJXWjAifV0sICJfc2RfYWxnIjogInNoYS0y" +
            "NTYiLCAiY25mIjogeyJqd2siOiB7Imt0eSI6ICJFQyIsICJjcnYiOiAiUC0yNTYiLCAieCI6ICJU" +
            "Q0FFUjE5WnZ1M09IRjRqNFc0dmZTVm9ISVAxSUxpbERsczd2Q2VHZW1jIiwgInkiOiAiWnhqaVdX" +
            "YlpNUUdIVldLVlE0aGJTSWlyc1ZmdWVjQ0U2dDRqVDlGMkhaUSJ9fX0." +
            "MczwjBFGtzf-6WMT-hIvYbkb11NrV1WMO-jTijpMPNbswNzZ87wY2uHz-CXo6R04b7jYrpj9mNRAvVssXou1iw" +
            "~WyIyR0xDNDJzS1F2ZUNmR2ZyeU5STjl3IiwgImdpdmVuX25hbWUiLCAiSm9obiJd" +
            "~WyJlbHVWNU9nM2dTTklJOEVZbnN4QV9BIiwgImZhbWlseV9uYW1lIiwgIkRvZSJd" +
            "~WyI2SWo3dE0tYTVpVlBHYm9TNXRtdlZBIiwgImVtYWlsIiwgImpvaG5kb2VAZXhhbXBsZS5jb20iXQ" +
            "~WyJlSThaV205UW5LUHBOUGVOZW5IZGhRIiwgInBob25lX251bWJlciIsICIrMS0yMDItNTU1LTAxMDEiXQ" +
            "~WyJRZ19PNjR6cUF4ZTQxMmExMDhpcm9BIiwgInBob25lX251bWJlcl92ZXJpZmllZCIsIHRydWVd" +
            "~WyJBSngtMDk1VlBycFR0TjRRTU9xUk9BIiwgImFkZHJlc3MiLCB7InN0cmVldF9hZGRyZXNzIjog" +
            "IjEyMyBNYWluIFN0IiwgImxvY2FsaXR5IjogIkFueXRvd24iLCAicmVnaW9uIjogIkFueXN0YXRlI" +
            "iwgImNvdW50cnkiOiAiVVMifV0" +
            "~WyJQYzMzSk0yTGNoY1VfbEhnZ3ZfdWZRIiwgImJpcnRoZGF0ZSIsICIxOTQwLTAxLTAxIl0" +
            "~WyJHMDJOU3JRZmpGWFE3SW8wOXN5YWpBIiwgInVwZGF0ZWRfYXQiLCAxNTcwMDAwMDAwXQ" +
            "~WyJsa2x4RjVqTVlsR1RQVW92TU5JdkNBIiwgIlVTIl0" +
            "~WyJuUHVvUW5rUkZxM0JJZUFtN0FuWEZBIiwgIkRFIl0~"
    }
}
