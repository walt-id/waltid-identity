@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.mdoc.mso

import cbor.Cbor
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.decodeFromHexString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class IdentifierListInfoTest {

    private val jsonFixtures = listOf(
        IdentifierListInfo(byteArrayOf(1), "https://example.invalid/list") to
                """{"id":[1],"uri":"https://example.invalid/list"}""",
        IdentifierListInfo(byteArrayOf(1), "https://example.invalid/list", byteArrayOf(2)) to
                """{"id":[1],"uri":"https://example.invalid/list","certificate":[2]}""",
    )
    private val conflictingIdentifierKeys = Json.parseToJsonElement(
        """{"id":[1],"idx":[3],"uri":"https://example.invalid/list"}""",
    ).jsonObject

    @Test
    fun jsonHelpersUseIdForIdentifierBytes() {
        for ((expected, literal) in jsonFixtures) {
            val json = Json.parseToJsonElement(literal).jsonObject
            assertEquals(json, expected.toJSON())
            assertEquals(expected, IdentifierListInfo.fromJSON(json))
        }
        assertEquals(jsonFixtures.first().first, IdentifierListInfo.fromJSON(conflictingIdentifierKeys))
    }

    @Test
    fun jsonSerializerUsesIdForIdentifierBytes() {
        for ((expected, literal) in jsonFixtures) {
            val json = Json.parseToJsonElement(literal)
            assertEquals(json, Json.encodeToJsonElement(expected))
            assertEquals(expected, Json.decodeFromJsonElement<IdentifierListInfo>(json))
        }
        assertEquals(jsonFixtures.first().first, Json.decodeFromJsonElement<IdentifierListInfo>(conflictingIdentifierKeys))
    }

    @Test
    fun jsonDecodersUseLegacyIdxOnlyWhenIdIsAbsent() {
        val json = Json.parseToJsonElement("""{"idx":[1],"uri":"https://example.invalid/list"}""").jsonObject
        val expected = IdentifierListInfo(byteArrayOf(1), "https://example.invalid/list")
        assertEquals(expected, IdentifierListInfo.fromJSON(json))
        assertEquals(expected, Json.decodeFromJsonElement<IdentifierListInfo>(json))

        val invalidCanonical = Json.parseToJsonElement(
            """{"id":null,"idx":[1],"uri":"https://example.invalid/list"}""",
        ).jsonObject
        assertFailsWith<SerializationException> { IdentifierListInfo.fromJSON(invalidCanonical) }
        assertFailsWith<SerializationException> { Json.decodeFromJsonElement<IdentifierListInfo>(invalidCanonical) }
    }

    @Test
    fun testHappyPathSerialize() {

        val payloads = listOf(
            IdentifierListInfo(
                id = ByteArray(5),
                uri = "https://some-example.com",
            ),
            IdentifierListInfo(
                id = ByteArray(0),
                uri = "https://some-example.com",
            ),
            IdentifierListInfo(
                id = ByteArray(5),
                uri = "https://some-example.com",
                certificate = ByteArray(5)
            ),
            IdentifierListInfo(
                id = ByteArray(5),
                uri = "https://some-example.com",
                certificate = ByteArray(0)
            ),
        )

        payloads.forEach { payload ->

            assertEquals(
                expected = payload,
                actual = IdentifierListInfo.fromCBOR(payload.toCBOR()),
            )

            assertEquals(
                expected = payload,
                actual = IdentifierListInfo.fromCBORHex(payload.toCBORHex()),
            )

            assertEquals(
                expected = payload,
                actual = IdentifierListInfo.fromMapElement(payload.toMapElement()),
            )

            assertEquals(
                expected = payload,
                actual = Cbor.decodeFromByteArray<IdentifierListInfo>(payload.toCBOR()),
            )

            assertEquals(
                expected = payload,
                actual = Cbor.decodeFromHexString<IdentifierListInfo>(payload.toCBORHex()),
            )
        }
    }

    @Test
    fun invalidPayloadsAsHexStrings() {

        listOf(
            "bf6375726964616c6c6fff", // { "uri": "allo"}
            "bf62696464616c6c6fff", //{ "id": "allo"}
            "bf626964646b6174696375726964616c6c6fff", // { "id": "kati", "uri": "allo"}
            "bf62696464616c6c6f6375726905ff", // { "id": "allo", "uri": 5}
        ).forEach {
            assertFails {
                IdentifierListInfo.fromCBORHex(it)
            }.let { throwable ->
                assertIs<IllegalArgumentException>(throwable)
            }
        }
    }
}
