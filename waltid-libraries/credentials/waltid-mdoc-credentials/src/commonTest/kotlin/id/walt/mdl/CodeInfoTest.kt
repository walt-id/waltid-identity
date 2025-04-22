@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.mdl

import cbor.Cbor
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.decodeFromHexString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.test.Test
import kotlin.test.assertEquals

class CodeInfoTest {

    private val payloads = listOf(
        CodeInfo(
            code = "some-code",
        ),
        CodeInfo(
            code = "some-code",
            sign = "some-sign",
        ),
        CodeInfo(
            code = "some-code",
            value = "some-value",
        ),
        CodeInfo(
            code = "some-code",
            sign = "some-sign",
            value = "some-value",
        ),
    )

    @Test
    fun testJsonSerialize() {

        payloads.forEach { payload ->

            assertEquals(
                expected = payload,
                actual = CodeInfo.fromJSON(payload.toJSON()),
            )

            assertEquals(
                expected = payload,
                actual = Json.decodeFromJsonElement(payload.toJSON()),
            )

            assertEquals(
                expected = payload,
                actual = Json.decodeFromJsonElement(Json.encodeToJsonElement(payload)),
            )

            assertEquals(
                expected = payload,
                actual = Json.decodeFromString<CodeInfo>(Json.encodeToString(payload)),
            )
        }
    }

    @Test
    fun testCborSerialize() {

        payloads.forEach { payload ->

            assertEquals(
                expected = payload,
                actual = CodeInfo.fromCBOR(payload.toCBOR()),
            )

            assertEquals(
                expected = payload,
                actual = CodeInfo.fromCBORHex(payload.toCBORHex()),
            )

            assertEquals(
                expected = payload,
                actual = CodeInfo.fromMapElement(payload.toMapElement()),
            )

            assertEquals(
                expected = payload,
                actual = Cbor.decodeFromByteArray<CodeInfo>(payload.toCBOR()),
            )

            assertEquals(
                expected = payload,
                actual = Cbor.decodeFromHexString<CodeInfo>(payload.toCBORHex()),
            )
        }

    }
}