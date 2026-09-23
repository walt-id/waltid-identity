package id.walt.crypto.utils

import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A CBOR byte string decodes to one JSON number per byte, which for a 250 KB portrait cost 2,888,910
 * bytes of BSON against 333,354 for the same bytes as base64url.
 */
class ByteArrayBase64ConversionTest {

    private val portrait = ByteArray(250_000) { (it % 256).toByte() }

    private fun asNumbers(bytes: ByteArray) = JsonArray(bytes.map { JsonPrimitive(it) })

    @Test
    fun aLongByteArrayBecomesBase64() {
        val converted = asNumbers(portrait).withByteArraysAsBase64().jsonObject
        assertEquals(BASE64_BYTES_TYPE, converted.getValue("type").jsonPrimitive.content)
        assertEquals(250_000, converted.getValue("length").jsonPrimitive.content.toInt())
        assertTrue(converted.toString().length < 400_000, "base64 form must be far smaller than the array")
    }

    @Test
    fun theConversionIsLossless() {
        // The point of base64 over truncation: the bytes can be recovered exactly.
        val converted = asNumbers(portrait).withByteArraysAsBase64().jsonObject
        val restored = converted.getValue("base64url").jsonPrimitive.content.decodeFromBase64Url()
        assertContentEquals(portrait, restored)
    }

    @Test
    fun everySignedByteValueSurvives() {
        // Byte values arrive signed, so the full -128..127 range has to round-trip.
        val all = ByteArray(256) { (it - 128).toByte() }
        val converted = asNumbers(all).withByteArraysAsBase64().jsonObject
        assertContentEquals(all, converted.getValue("base64url").jsonPrimitive.content.decodeFromBase64Url())
    }

    @Test
    fun shortArraysStayArrays() {
        // A digest or a coordinate pair is more useful readable than as base64.
        val digest = asNumbers(ByteArray(32) { it.toByte() })
        assertEquals(digest, digest.withByteArraysAsBase64())
    }

    @Test
    fun arraysOutsideTheByteRangeAreUntouched() {
        // An array of larger numbers is not a byte string and must keep its shape.
        val numbers = buildJsonArray { repeat(100) { add(JsonPrimitive(it * 1000)) } }
        assertEquals(numbers, numbers.withByteArraysAsBase64())
    }

    @Test
    fun arraysOfStringsAreUntouched() {
        val strings = buildJsonArray { repeat(100) { add(JsonPrimitive("value-$it")) } }
        assertEquals(strings, strings.withByteArraysAsBase64())
    }

    @Test
    fun aNestedPortraitIsFound() {
        // The portrait sits under namespace and element keys, never at the top level.
        val nested = buildJsonObject {
            put("org.iso.18013.5.1", buildJsonObject {
                put("family_name", "Doe")
                put("portrait", asNumbers(portrait))
            })
        }
        val ns = nested.withByteArraysAsBase64().jsonObject.getValue("org.iso.18013.5.1").jsonObject
        assertEquals("Doe", ns.getValue("family_name").jsonPrimitive.content)
        assertEquals(BASE64_BYTES_TYPE, ns.getValue("portrait").jsonObject.getValue("type").jsonPrimitive.content)
    }

    @Test
    fun smallValuesOfEveryKindSurvive() {
        val claims = buildJsonObject {
            put("family_name", "Doe")
            put("age_over_18", true)
            put("issue_count", 7)
            put("birth_date", "1985-03-15")
        }
        assertEquals(claims, claims.withByteArraysAsBase64())
    }
}
