@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

import id.walt.crypto.utils.JsonUtils.toJsonElement
import kotlinx.serialization.cbor.CborArray
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.cbor.CborInteger
import kotlinx.serialization.cbor.CborString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * A CBOR array of whole numbers must reach JSON without an object per element.
 *
 * A heap dump of a verifier holding two presentations of a 230,000 element claim contained 920,667 live
 * `JsonLiteral`, 1,066,730 `String` and 1,076,286 `byte[]` instances, retained for the lifetime of the session.
 * Byte strings already avoided this through a flyweight; arrays of numbers did not, which is what a binary claim
 * looks like whenever it reaches CBOR as an array rather than a byte string.
 *
 * The representation must not change - only what it costs to hold - so equality, hash code and serialised text
 * are all pinned against the eager form.
 */
class JsonIntegerArrayTest {

    @Test
    fun integerArrayKeepsItsRepresentation() {
        val values = longArrayOf(-9007199254740991L, -128, -1, 0, 1, 127, 255, 9007199254740991L)
        val cbor = CborArray(values.map { CborInteger(it) })
        val expected = JsonArray(values.map { JsonPrimitive(it) })

        val actual = cbor.toJsonElement()

        assertEquals(expected, actual)
        assertEquals(expected.hashCode(), actual.hashCode())
        assertEquals(
            Json.encodeToString(JsonElement.serializer(), expected),
            Json.encodeToString(JsonElement.serializer(), actual),
        )
    }

    @Test
    fun largeIntegerArrayIsNotMaterialisedElementByElement() {
        // 230_000 is the size that exhausted a verifier's heap. The assertion is on identity of the repeated
        // reads rather than on memory: a flyweight computes the element on demand, so two reads of the same
        // index are equal but need not be the same instance, while an eagerly built list returns one instance.
        val size = 230_000
        val cbor = CborArray((0 until size).map { CborInteger((it % 251).toLong()) })

        val json = cbor.toJsonElement()

        assertTrue(json is JsonArray)
        assertEquals(size, json.size)
        assertEquals(JsonPrimitive(0L), json[0])
        assertEquals(JsonPrimitive(((size - 1) % 251).toLong()), json[size - 1])
        assertTrue(
            json[0] !== json[0],
            "elements must be produced on demand: an eagerly built list hands back the same retained instance",
        )
    }

    @Test
    fun mixedArraysStillConvertElementByElement() {
        val cbor = CborArray(listOf(CborInteger(1), CborString("two"), CborInteger(3)))

        val json = assertIs<JsonArray>(cbor.toJsonElement())

        assertEquals(
            JsonArray(listOf(JsonPrimitive(1L), JsonPrimitive("two"), JsonPrimitive(3L))),
            json,
        )
        // The counterpart of the assertion above: a mixed array is still converted eagerly, so its elements
        // are retained instances.
        assertTrue(json[0] === json[0], "a mixed array keeps the element-by-element conversion")
    }

    @Test
    fun emptyAndNestedArraysAreUnchanged() {
        assertEquals(JsonArray(emptyList()), CborArray(emptyList<CborElement>()).toJsonElement())

        val nested = CborArray(listOf(CborArray(listOf(CborInteger(1), CborInteger(2)))))
        assertEquals(
            JsonArray(listOf(JsonArray(listOf(JsonPrimitive(1L), JsonPrimitive(2L))))),
            nested.toJsonElement(),
        )
    }
}
