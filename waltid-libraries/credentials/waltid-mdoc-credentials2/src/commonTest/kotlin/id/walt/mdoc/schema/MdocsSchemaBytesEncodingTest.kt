package id.walt.mdoc.schema

import id.walt.mdoc.schema.MdocsSchema.MdocsDatatype
import id.walt.mdoc.schema.MdocsSchema.MdocsSchemaType
import kotlinx.serialization.cbor.CborByteString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * How a `BYTES` element crosses the CBOR/JSON boundary.
 *
 * It used to become a JSON array with one entry per byte, which costs a `JsonLiteral` plus a `String` plus a
 * backing array per byte - roughly 80 bytes of heap for one byte of payload. A single 230 KB portrait therefore
 * produced ~460,000 objects, and a verifier that retained a few such sessions exhausted a 768 MiB heap in
 * minutes. It is one base64url string now.
 *
 * The round trip is the part that must not break: an mdoc's digests are computed over the CBOR, so a byte string
 * that came back as a CBOR *text* string would still parse, still look right in JSON, and fail signature
 * verification - only for credentials carrying binary fields. That is asserted here on both the type and the
 * bytes.
 */
class MdocsSchemaBytesEncodingTest {

    private val bytesType = MdocsSchemaType(MdocsDatatype.BYTES)

    /** Includes bytes above 0x7F, which the old representation emitted as negative numbers. */
    private val payload = ByteArray(1024) { (it * 7 + 0x89).toByte() }

    private fun toJson(bytes: ByteArray): JsonElement = with(MdocsSchemaMappingFunction) {
        CborByteString(bytes).schemafulToJsonElement(bytesType)
    }

    private fun toCborBytes(json: JsonElement): CborByteString = with(MdocsSchemaMappingFunction) {
        assertIs<CborByteString>(
            json.schemafulJsonToCborElement(bytesType),
            "a BYTES element must return to CBOR as a byte string - a text string would break the mdoc digests",
        )
    }

    @Test
    fun `bytes survive the CBOR to JSON to CBOR round trip unchanged`() {
        val restored = toCborBytes(toJson(payload))

        assertContentEquals(payload, restored.toByteArray(), "the payload must be identical after the round trip")
    }

    @Test
    fun `bytes survive being serialised as JSON text and parsed back`() {
        // The path that matters for persistence and for API clients: the value goes out as text and comes back.
        val text = Json.encodeToString(JsonElement.serializer(), toJson(payload))
        val restored = toCborBytes(Json.parseToJsonElement(text))

        assertContentEquals(payload, restored.toByteArray())
    }

    @Test
    fun `a byte string is a single JSON value instead of one value per byte`() {
        val encoded = toJson(payload)

        assertIs<JsonPrimitive>(encoded, "one value, not an array of ${payload.size} values")
        val legacy = buildJsonArray { payload.forEach { add(JsonPrimitive(it)) } }
        val encodedLength = Json.encodeToString(JsonElement.serializer(), encoded).length
        val legacyLength = Json.encodeToString(JsonElement.serializer(), legacy).length
        // Text size is the lesser win; the object count is the reason this changed. Asserted loosely so the
        // test pins the shape rather than a particular ratio.
        assertTrue(
            encodedLength * 2 < legacyLength,
            "expected the base64 form to be far smaller, got $encodedLength against $legacyLength",
        )
    }

    @Test
    fun `the legacy per-byte array form is still accepted`() {
        // Credential data already persisted, and clients already sending it, carry the array shape.
        val legacy: JsonElement = buildJsonArray { payload.forEach { add(JsonPrimitive(it)) } }

        assertContentEquals(payload, toCborBytes(legacy).toByteArray())
    }

    @Test
    fun `decodeByScheme accepts both representations`() = with(MdocsSchemaMappingFunction) {
        val fromBase64 = toJson(payload).decodeByScheme(bytesType)
        val fromArray = buildJsonArray { payload.forEach { add(JsonPrimitive(it)) } }.decodeByScheme(bytesType)

        assertContentEquals(payload, assertIs<ByteArray>(fromBase64))
        assertContentEquals(payload, assertIs<ByteArray>(fromArray))
    }

    @Test
    fun `an empty byte string round trips`() {
        val empty = ByteArray(0)

        assertEquals(0, toCborBytes(toJson(empty)).toByteArray().size)
        assertContentEquals(empty, toCborBytes(JsonArray(emptyList())).toByteArray())
    }
}
